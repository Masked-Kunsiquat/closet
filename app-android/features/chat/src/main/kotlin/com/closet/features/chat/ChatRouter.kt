package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-LLM intent router for the chat feature.
 *
 * Intercepts user messages that have unambiguous, data-only answers and returns
 * them directly from DAO queries without going through the RAG + provider pipeline.
 * Faster, zero token cost, works fully offline.
 *
 * Three patterns are handled (per the Phase 2 spec — don't expand speculatively):
 * 1. "How many times have I worn [item]?" → [ClothingDao.getWearCountByName]
 * 2. "What haven't I worn in [N] days?"   → [ClothingDao.getItemsNotWornSince]
 * 3. "What did I wear on [date]?"         → [LogDao.getLogsForDateOnce]
 *
 * If a pattern doesn't match confidently, or the DAO returns an ambiguous result,
 * [RouterResult.Unrouted] is returned and the caller falls through to RAG.
 */
@Singleton
class ChatRouter @Inject constructor(
    private val clothingDao: ClothingDao,
    private val logDao: LogDao,
) {

    sealed interface RouterResult {
        data class Routed(val response: ChatResponse) : RouterResult
        data object Unrouted : RouterResult
    }

    suspend fun route(message: String): RouterResult {
        val lower = message.lowercase(Locale.ENGLISH).trim()

        return when {
            matchesWearCount(lower)   -> routeWearCount(lower)
            matchesNotWornSince(lower) -> routeNotWornSince(lower)
            matchesWornOn(lower)      -> routeWornOn(lower)
            else                      -> RouterResult.Unrouted
        }
    }

    // ── Pattern matching ──────────────────────────────────────────────────────

    private fun matchesWearCount(lower: String): Boolean =
        ("how many times" in lower || "wear count" in lower) &&
        ("worn" in lower || "wear" in lower)

    private fun matchesNotWornSince(lower: String): Boolean =
        ("haven't worn" in lower || "havent worn" in lower ||
         "not worn" in lower || "unworn" in lower) &&
        (DAYS_PATTERN.containsMatchIn(lower) || "lately" in lower || "recently" in lower)

    private fun matchesWornOn(lower: String): Boolean =
        "what did i wear on" in lower ||
        "what was i wearing on" in lower ||
        "wore on" in lower

    // ── Routing handlers ─────────────────────────────────────────────────────

    private suspend fun routeWearCount(lower: String): RouterResult {
        val itemName = extractItemName(lower) ?: run {
            Timber.d("ChatRouter: wear-count pattern matched but item name not extractable")
            return RouterResult.Unrouted
        }
        val result = clothingDao.getWearCountByName(itemName) ?: run {
            Timber.d("ChatRouter: no item matched name query '%s'", itemName)
            return RouterResult.Unrouted
        }
        val timesLabel = if (result.wearCount == 1) "time" else "times"
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = "You've worn your ${result.name} ${result.wearCount} $timesLabel.",
                label = "Wear count",
                value = "${result.wearCount} $timesLabel",
                itemIds = listOf(result.id),
            )
        )
    }

    private suspend fun routeNotWornSince(lower: String): RouterResult {
        val days = extractDays(lower) ?: DEFAULT_UNWORN_DAYS
        val cutoffDate = LocalDate.now().minusDays(days.toLong()).toString()
        val items = clothingDao.getItemsNotWornSince(cutoffDate)

        val itemsLabel = if (items.size == 1) "item" else "items"
        val text = if (items.isEmpty()) {
            "You've worn everything in the last $days days — nice work!"
        } else {
            "You have ${items.size} $itemsLabel you haven't worn in the last $days days."
        }
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = text,
                label = "Unworn for $days+ days",
                value = "${items.size} $itemsLabel",
                itemIds = items.map { it.id },
            )
        )
    }

    private suspend fun routeWornOn(lower: String): RouterResult {
        val date = extractDate(lower) ?: run {
            Timber.d("ChatRouter: worn-on pattern matched but date not parseable")
            return RouterResult.Unrouted
        }
        val logs = logDao.getLogsForDateOnce(date)
        val displayDate = formatDisplayDate(date)

        val text = when {
            logs.isEmpty() -> "No outfit was logged for $displayDate."
            else -> {
                val names = logs.mapNotNull { it.outfitName }.distinct()
                if (names.isEmpty()) "You logged a wear on $displayDate."
                else "On $displayDate you wore: ${names.joinToString(", ")}."
            }
        }
        val outfitsLabel = if (logs.size == 1) "outfit" else "outfits"
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = text,
                label = "Worn on $displayDate",
                value = if (logs.isEmpty()) "Nothing logged" else "${logs.size} $outfitsLabel",
                itemIds = emptyList(),
            )
        )
    }

    // ── Extraction helpers ────────────────────────────────────────────────────

    /**
     * Extracts an item name from a wear-count query.
     * Matches patterns like "how many times have i worn my grey blazer?" → "grey blazer".
     * Returns null if no name can be confidently extracted.
     */
    private fun extractItemName(lower: String): String? {
        val match = ITEM_NAME_PATTERN.find(lower) ?: return null
        return match.groupValues[1].trim().removeSuffix("?").trim().takeIf { it.isNotBlank() }
    }

    /**
     * Extracts a day count from a not-worn-since query.
     * Matches "30 days", "2 weeks" (converted to days). Returns null for "lately"/"recently".
     */
    private fun extractDays(lower: String): Int? {
        val match = DAYS_PATTERN.find(lower) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        return if ("week" in unit) count * 7 else count
    }

    /**
     * Extracts and parses a date string from a worn-on query.
     *
     * Only handles unambiguous formats:
     * - ISO: `2026-04-04`
     * - Month Day: `April 4`, `April 4th`, `Apr 4`
     * - Month Day Year: `April 4, 2026`
     *
     * Returns an ISO date string (YYYY-MM-DD) or null if unparseable.
     */
    private fun extractDate(lower: String): String? {
        // ISO date
        ISO_DATE_PATTERN.find(lower)?.value?.let { return it }

        // "Month Day" or "Month Day, Year"
        val monthMatch = MONTH_DAY_PATTERN.find(lower) ?: return null
        val monthStr = monthMatch.groupValues[1]
        val dayStr = monthMatch.groupValues[2].replace(Regex("st|nd|rd|th"), "")
        val yearStr = monthMatch.groupValues[3].trim().trimStart(',').trim()

        val year = yearStr.toIntOrNull() ?: LocalDate.now().year
        val monthNum = MONTH_NAMES[monthStr.lowercase(Locale.ENGLISH)] ?: return null

        return try {
            LocalDate.of(year, monthNum, dayStr.toInt()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDisplayDate(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
    } catch (e: Exception) {
        isoDate
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_UNWORN_DAYS = 30

        private val ITEM_NAME_PATTERN = Regex(
            """(?:how many times (?:have i |did i |i've )?worn|worn) (?:my |the )?(.+?)(?:\?|$)"""
        )

        private val DAYS_PATTERN = Regex("""(\d+)\s*(days?|weeks?)""")

        private val ISO_DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

        private val MONTH_DAY_PATTERN = Regex(
            """(january|february|march|april|may|june|july|august|september|october|november|december|""" +
            """jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\.?\s+(\d{1,2}(?:st|nd|rd|th)?),?\s*(\d{4})?""",
            RegexOption.IGNORE_CASE
        )

        private val MONTH_NAMES = mapOf(
            "january" to 1, "jan" to 1,
            "february" to 2, "feb" to 2,
            "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4,
            "may" to 5,
            "june" to 6, "jun" to 6,
            "july" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9,
            "october" to 10, "oct" to 10,
            "november" to 11, "nov" to 11,
            "december" to 12, "dec" to 12,
        )
    }
}
