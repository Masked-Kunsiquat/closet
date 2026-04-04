package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val dateParser: ChatDateParser,
) {

    sealed interface RouterResult {
        data class Routed(val response: ChatResponse) : RouterResult
        data object Unrouted : RouterResult
    }

    // Language identifier configured with a 0.7 confidence threshold.
    // Returns "und" (undetermined) when confidence < threshold — treated as non-English.
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(LANGUAGE_CONFIDENCE_THRESHOLD)
            .build()
    )

    suspend fun route(message: String): RouterResult {
        // Language guard: only route if the message is confidently English.
        // Non-English or low-confidence input falls through to RAG, which handles
        // multilingual prompts naturally via the provider.
        if (!isEnglish(message)) return RouterResult.Unrouted

        val lower = message.lowercase(Locale.ENGLISH).trim()

        return when {
            matchesWearCount(lower)    -> routeWearCount(lower)
            matchesNotWornSince(lower) -> routeNotWornSince(lower)
            matchesWornOn(lower)       -> routeWornOn(lower)
            else                       -> RouterResult.Unrouted
        }
    }

    /**
     * Returns true if ML Kit identifies [text] as English with confidence ≥ [LANGUAGE_CONFIDENCE_THRESHOLD].
     * On any identification failure, logs the error and returns true so the router still runs —
     * a false negative (routing a non-English query) is less harmful than silently dropping
     * all routing for the session.
     */
    private suspend fun isEnglish(text: String): Boolean = try {
        languageIdentifier.identifyLanguage(text).await() == "en"
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.w(e, "ChatRouter: language identification failed, proceeding with routing")
        true
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
        val date = dateParser.parseDate(lower) ?: run {
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

    private fun formatDisplayDate(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
    } catch (e: Exception) {
        isoDate
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_UNWORN_DAYS = 30
        private const val LANGUAGE_CONFIDENCE_THRESHOLD = 0.7f

        private val ITEM_NAME_PATTERN = Regex(
            """(?:how many times (?:have i |did i |i've )?worn|worn) (?:my |the )?(.+?)(?:\?|$)"""
        )

        private val DAYS_PATTERN = Regex("""(\d+)\s*(days?|weeks?)""")
    }
}
