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
 * Full-flavor [ChatRouter]: uses ML Kit Language Identification to gate English-only
 * pattern matching, then routes stat queries directly from DAO results.
 *
 * Intercepts user messages that have unambiguous, data-only answers and returns
 * them directly from DAO queries without going through the RAG + provider pipeline.
 * Faster, zero token cost, works fully offline.
 *
 * Eight patterns are handled:
 * 1. "How many times have I worn [item]?" → [ClothingDao.getWearCountByName]
 * 2. "What haven't I worn in [N] days?"   → [ClothingDao.getItemsNotWornSince]
 * 3. "What did I wear on [date]?"         → [LogDao.getLogsForDateOnce]
 * 4. "What have I never worn?"            → [ClothingDao.getItemsNeverWorn]
 * 5. "What's in my laundry?"             → [ClothingDao.getItemsNeedingWash]
 * 6. "What did I wear last?"             → [LogDao.getMostRecentLog]
 * 7. "How many items do I own?"          → [ClothingDao.getItemCount]
 * 8. "What's my most worn item?"         → [ClothingDao.getMostWornItem]
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
            // Order matters: neverWorn before notWornSince (both contain "worn")
            matchesNeverWorn(lower)    -> routeNeverWorn()
            matchesWearCount(lower)    -> routeWearCount(lower)
            matchesNotWornSince(lower) -> routeNotWornSince(lower)
            matchesWornOn(lower)       -> routeWornOn(lower)
            matchesNeedsWash(lower)    -> routeNeedsWash()
            matchesLastOutfit(lower)   -> routeLastOutfit()
            matchesItemCount(lower)    -> routeItemCount()
            matchesMostWorn(lower)     -> routeMostWorn()
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
        WORE_ON_INTERROGATIVE_PATTERN.containsMatchIn(lower)

    private fun matchesNeverWorn(lower: String): Boolean =
        "never worn" in lower ||
        "never been worn" in lower ||
        ("never" in lower && "wear" in lower)

    private fun matchesNeedsWash(lower: String): Boolean =
        "laundry" in lower ||
        "needs washing" in lower ||
        "needs to be washed" in lower ||
        "need to wash" in lower ||
        ("dirty" in lower && ("clothes" in lower || "items" in lower || "what" in lower))

    private fun matchesLastOutfit(lower: String): Boolean =
        "what did i wear last" in lower ||
        "what was i wearing last" in lower ||
        "last outfit" in lower ||
        "wore last" in lower ||
        "most recent outfit" in lower

    private fun matchesItemCount(lower: String): Boolean =
        ("how many" in lower && ("items" in lower || "clothes" in lower || "pieces" in lower)) ||
        "how big is my wardrobe" in lower ||
        "size of my wardrobe" in lower

    private fun matchesMostWorn(lower: String): Boolean =
        "most worn" in lower ||
        "wear the most" in lower ||
        "what do i wear most" in lower ||
        "most frequently worn" in lower

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
        // Use strict "> cutoffDate" semantics: an item last worn exactly `days` days ago
        // should appear in the results ("haven't worn in 30 days" includes day-30 boundary).
        // The DAO predicate is now "ol.date > :cutoffDate" (exclusive), so cutoffDate = today - days.
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

    private suspend fun routeNeverWorn(): RouterResult {
        val items = clothingDao.getItemsNeverWorn()
        val itemsLabel = if (items.size == 1) "item" else "items"
        val text = if (items.isEmpty()) {
            "You've worn everything in your wardrobe — impressive!"
        } else {
            "You have ${items.size} $itemsLabel you've never worn."
        }
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = text,
                label = "Never worn",
                value = "${items.size} $itemsLabel",
                itemIds = items.map { it.id },
            )
        )
    }

    private suspend fun routeNeedsWash(): RouterResult {
        val items = clothingDao.getItemsNeedingWash()
        val itemsLabel = if (items.size == 1) "item" else "items"
        val text = if (items.isEmpty()) {
            "Your laundry is all clear — nothing needs washing."
        } else {
            "You have ${items.size} $itemsLabel in your laundry."
        }
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = text,
                label = "Needs washing",
                value = "${items.size} $itemsLabel",
                itemIds = items.map { it.id },
            )
        )
    }

    private suspend fun routeLastOutfit(): RouterResult {
        val log = logDao.getMostRecentLog() ?: return RouterResult.Routed(
            ChatResponse.WithStat(
                text = "You haven't logged any outfits yet.",
                label = "Last worn",
                value = "Nothing logged",
            )
        )
        val displayDate = formatDisplayDate(log.date)
        val text = if (log.outfitName != null) {
            "Your last logged outfit was ${log.outfitName} on $displayDate."
        } else {
            "Your last logged wear was on $displayDate."
        }
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = text,
                label = "Last worn",
                value = displayDate,
            )
        )
    }

    private suspend fun routeItemCount(): RouterResult {
        val count = clothingDao.getItemCount()
        val itemsLabel = if (count == 1) "item" else "items"
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = "You have $count $itemsLabel in your wardrobe.",
                label = "Wardrobe size",
                value = "$count $itemsLabel",
            )
        )
    }

    private suspend fun routeMostWorn(): RouterResult {
        val result = clothingDao.getMostWornItem() ?: return RouterResult.Routed(
            ChatResponse.WithStat(
                text = "You haven't logged any wears yet.",
                label = "Most worn",
                value = "Nothing logged",
            )
        )
        val timesLabel = if (result.wearCount == 1) "time" else "times"
        return RouterResult.Routed(
            ChatResponse.WithStat(
                text = "Your most worn item is the ${result.name}, worn ${result.wearCount} $timesLabel.",
                label = "Most worn",
                value = "${result.wearCount} $timesLabel",
                itemIds = listOf(result.id),
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

        // Matches "what ... i wore on" with at most ~15 chars between "what" and "i" so that
        // incidental uses like "what goes with what I wore on Tuesday?" don't trigger routing.
        private val WORE_ON_INTERROGATIVE_PATTERN = Regex("""\bwhat\b.{0,15}\bi\b\s*\bwore on\b""")
    }
}
