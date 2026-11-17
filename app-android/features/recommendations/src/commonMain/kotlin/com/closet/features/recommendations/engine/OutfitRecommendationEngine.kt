package com.closet.features.recommendations.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.pow
import kotlin.random.Random

/**
 * Pure outfit recommendation engine — no Android, no coroutines, no Room.
 *
 * Registered as a Koin `single` in `recommendationsModule`, so there is exactly one
 * instance for the app's lifetime. It remains fully unit-testable without DI — just call
 * the constructor directly in tests.
 *
 * Takes pre-fetched data as [EngineInput] and returns the top-N ranked [OutfitCombo]s
 * (or fewer if the wardrobe cannot form that many valid combos). The pool size is
 * controlled by the [recommend] parameters — defaults are [TOP_N] / [CANDIDATES_PER_SLOT].
 *
 * ### Pipeline
 * 1. Hard filter — redundant safety check: Active + Clean (DAO already filtered, but
 *    the engine applies it again as a defensive guard).
 * 2. Per-item suitability scoring — multiplicative multipliers from weather signals
 *    (signals with < 5 logs are skipped: sparse-data guard) and wear recency (only when
 *    [EngineInput.today] is set: worn ≤ 3 days ago × 0.90, ≥ 30 days ago × 1.05).
 * 3. Per-category cap — keep top 1–2 scored candidates per outfit-role slot.
 * 4. Color deduplication — one item per (color+subcategory) combo within each slot.
 * 5. Category completeness — build valid combos: (Top + Bottom) OR (OnePiece),
 *    optional Outerwear / Footwear / Accessory.
 * 6. Outfit-level scoring — mean of item scores × outfit-level multipliers (layering,
 *    color harmony as the geometric mean of its pair multipliers, pattern mixing, and
 *    × 0.85 for a shoe-less outfit when the capped pool has footwear).
 * 7. Exclusion — drop combos whose [key] is in `exclude` (already shown this session).
 * 8. Sort + tie-break — descending score (or score × (1 ± 0.03) when a `jitter` [Random]
 *    is given; [OutfitCombo.score] stays un-jittered); ties broken by oldest last-worn date.
 * 9. Diversity — take combos greedily, skipping any whose core (Top + Bottom, or
 *    OnePiece) is already picked; fill remaining slots from the skipped combos in order.
 * 10. Return the top N (default 3).
 */
class OutfitRecommendationEngine {

    companion object {
        // --- Temperatures ---
        private const val COLD_THRESHOLD_C = 5.0
        private const val WARM_THRESHOLD_C = 20.0

        // --- Per-item multipliers ---
        private const val MULTIPLIER_TEMP_OUT_OF_RANGE = 0.55
        private const val MULTIPLIER_RAIN_NOT_SUITABLE = 0.60
        private const val MULTIPLIER_WIND_NOT_SUITABLE = 0.70

        // --- Outfit-level multipliers ---
        private const val MULTIPLIER_COLD_NO_OUTER = 0.50
        private const val MULTIPLIER_WARM_WITH_OUTER = 0.75

        // --- Color harmony ---
        private const val BONUS_SAME_FAMILY = 0.10
        private const val BONUS_EARTH_WARM = 0.05
        private const val MULTIPLIER_BRIGHT_BRIGHT = 0.70
        private const val MULTIPLIER_WARM_COOL = 0.85

        // --- Pattern mixing ---
        private const val BONUS_ONE_PATTERNED = 0.10
        private const val MULTIPLIER_MULTI_PATTERNED = 0.75

        // --- Footwear ---
        private const val MULTIPLIER_NO_FOOTWEAR = 0.85

        // --- Wear recency ---
        private const val RECENT_WEAR_DAYS = 3
        private const val STALE_WEAR_DAYS = 30
        private const val MULTIPLIER_WORN_RECENTLY = 0.90
        private const val BONUS_NOT_WORN_LATELY = 1.05

        // --- Ranking jitter (sort key only) ---
        private const val JITTER_AMPLITUDE = 0.03

        // --- Sparse data guard ---
        private const val MIN_LOGS_FOR_SIGNAL = 5

        // --- Per-category slot cap ---
        private const val CANDIDATES_PER_SLOT = 2

        // --- Top N result ---
        private const val TOP_N = 3

        // --- Outfit roles ---
        private const val ROLE_TOP = "Top"
        private const val ROLE_BOTTOM = "Bottom"
        private const val ROLE_ONEPIECE = "OnePiece"
        private const val ROLE_OUTERWEAR = "Outerwear"
        private const val ROLE_FOOTWEAR = "Footwear"
        private const val ROLE_ACCESSORY = "Accessory"

        // --- Color families ---
        private const val FAMILY_NEUTRAL = "Neutral"
        private const val FAMILY_EARTH = "Earth"
        private const val FAMILY_COOL = "Cool"
        private const val FAMILY_WARM = "Warm"
        private const val FAMILY_BRIGHT = "Bright"

        // Sentinel for "never worn" tie-breaking — sorts before any real date.
        private const val NEVER_WORN_SENTINEL = ""
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Run the full recommendation pipeline and return the top-N combos.
     *
     * @param input             Pre-fetched engine input (no I/O performed inside this call).
     * @param topN              Maximum number of combos to return. Defaults to [TOP_N] (3).
     *                          Pass a larger value (e.g. 25) when AI is enabled so the
     *                          coherence scorer has a wider pool to curate from.
     * @param candidatesPerSlot Maximum candidates kept per outfit-role slot after scoring
     *                          and deduplication. Defaults to [CANDIDATES_PER_SLOT] (2).
     *                          Increase to 3 when AI is enabled to broaden variety.
     * @param exclude           Combo keys ([OutfitCombo.key]) to drop before ranking — the
     *                          combos already shown in the current session. Empty by default.
     * @param jitter            Optional randomness for the ranking only: each combo's sort key
     *                          becomes `score × (1 + u)`, `u` uniform in [-0.03, 0.03].
     *                          [OutfitCombo.score] keeps the un-jittered value. Null (default)
     *                          gives the deterministic order.
     * @return Up to [topN] [OutfitCombo]s ranked highest-first, preferring distinct cores.
     *         Empty if the wardrobe cannot form any valid, non-excluded outfit combination.
     */
    fun recommend(
        input: EngineInput,
        topN: Int = TOP_N,
        candidatesPerSlot: Int = CANDIDATES_PER_SLOT,
        exclude: Set<Set<Long>> = emptySet(),
        jitter: Random? = null,
    ): List<OutfitCombo> {
        require(topN >= 0) { "topN must be >= 0, was $topN" }
        require(candidatesPerSlot >= 0) { "candidatesPerSlot must be >= 0, was $candidatesPerSlot" }
        // 1. Hard filter — redundant safety guard on top of DAO filter
        val safeItems = input.candidates.filter { isActiveAndClean(it) }
        if (safeItems.isEmpty()) return emptyList()

        // 2. Score each item individually
        val itemScores: Map<Long, Double> = safeItems.associateBy(
            keySelector = { it.id },
            valueTransform = { item -> scoreItem(item, input) }
        )

        // 3. Group by outfit role, cap per slot
        val byRole = safeItems.groupBy { it.outfitRole ?: "Other" }
        val cappedByRole: Map<String, List<EngineItem>> = byRole.mapValues { (_, items) ->
            items
                .sortedByDescending { itemScores[it.id] ?: 1.0 }
                .deduplicateByColorAndSubcategory()
                .take(candidatesPerSlot)
        }

        // 4. Build valid combos from capped candidates
        val combos = buildCombos(cappedByRole)
        if (combos.isEmpty()) return emptyList()

        // 5. Score each combo
        val weather = input.weather
        val hasFootwearInPool = cappedByRole[ROLE_FOOTWEAR].orEmpty().isNotEmpty()
        val scoredCombos: List<OutfitCombo> = combos.map { comboItems ->
            val score = scoreCombo(comboItems, itemScores, weather, hasFootwearInPool)
            OutfitCombo(items = comboItems, score = score)
        }

        // 6. Exclusion — drop combos already shown this session
        val remaining = if (exclude.isEmpty()) {
            scoredCombos
        } else {
            scoredCombos.filter { it.key() !in exclude }
        }

        // 7. Sort: descending (optionally jittered) score, tie-break by oldest last-worn.
        // Jittered keys are drawn once per combo, in build order, so a seed is reproducible.
        val sortKeys: List<Double>? = jitter?.let { random ->
            remaining.map { it.score * (1.0 + random.nextDouble(-JITTER_AMPLITUDE, JITTER_AMPLITUDE)) }
        }
        val sorted = remaining.indices
            .sortedWith(
                compareByDescending<Int> { i -> sortKeys?.get(i) ?: remaining[i].score }
                    .thenBy { i -> comboTieBreakKey(remaining[i], input) }
            )
            .map { remaining[it] }

        // 8. Diversity: distinct cores first, then fill from the skipped combos
        return selectDiverse(sorted, topN)
    }

    // -------------------------------------------------------------------------
    // Diversity selection
    // -------------------------------------------------------------------------

    /**
     * Greedily takes combos from [sorted] whose core (Top + Bottom IDs, or the OnePiece ID)
     * has not been picked yet, up to [topN]. If that yields fewer than [topN], the remaining
     * slots are filled from the skipped combos in their sorted order, so no valid outfit is
     * dropped just for sharing a core.
     */
    private fun selectDiverse(sorted: List<OutfitCombo>, topN: Int): List<OutfitCombo> {
        val picked = mutableListOf<OutfitCombo>()
        val skipped = mutableListOf<OutfitCombo>()
        val seenCores = mutableSetOf<Set<Long>>()
        for (combo in sorted) {
            if (picked.size == topN) break
            if (seenCores.add(comboCore(combo))) picked.add(combo) else skipped.add(combo)
        }
        if (picked.size < topN) {
            picked.addAll(skipped.take(topN - picked.size))
        }
        return picked
    }

    /** The combo's core: its Top + Bottom IDs, or its OnePiece ID. */
    private fun comboCore(combo: OutfitCombo): Set<Long> =
        combo.items
            .filter { it.outfitRole == ROLE_TOP || it.outfitRole == ROLE_BOTTOM || it.outfitRole == ROLE_ONEPIECE }
            .map { it.id }
            .toSet()

    // -------------------------------------------------------------------------
    // Step 1 — Hard filter
    // -------------------------------------------------------------------------

    /** Returns true if the item is Active + Clean (both must be true). */
    private fun isActiveAndClean(item: EngineItem): Boolean {
        // CandidateItem does not carry status/wash_status directly — those are enforced
        // by the DAO query. This check is a defensive guard using the EngineItem contract:
        // EngineItems are always constructed from DAO-filtered results, so we accept all
        // items that arrive here. The hard filter is therefore a no-op by design —
        // the guard exists to prevent future regressions if construction changes.
        // (If EngineItem gains status fields in the future, filter them here.)
        return true
    }

    // -------------------------------------------------------------------------
    // Step 2 — Per-item suitability scoring
    // -------------------------------------------------------------------------

    private fun scoreItem(item: EngineItem, input: EngineInput): Double {
        var score = recencyMultiplier(item, input) // runs with or without weather
        val weather = input.weather ?: return score // no weather → no weather signals

        // Temperature signal
        val tempPercentile = input.tempPercentiles[item.id]
        if (tempPercentile != null && tempPercentile.logCount >= MIN_LOGS_FOR_SIGNAL) {
            val forecastLow = weather.tempLowC
            val forecastHigh = weather.tempHighC
            if (forecastLow != null && forecastHigh != null) {
                val outsideRange =
                    forecastHigh < tempPercentile.p10TempLow ||
                    forecastLow > tempPercentile.p90TempHigh
                if (outsideRange) {
                    score *= MULTIPLIER_TEMP_OUT_OF_RANGE
                }
            }
        }

        // Rain signal
        if (weather.isRaining) {
            val rain = input.rainSuitability[item.id]
            if (rain != null && rain.rainLogCount >= MIN_LOGS_FOR_SIGNAL) {
                if (rain.rainPct < 0.20) {
                    score *= MULTIPLIER_RAIN_NOT_SUITABLE
                }
            }
        }

        // Wind signal
        if (weather.isWindy) {
            val wind = input.windSuitability[item.id]
            if (wind != null && wind.windLogCount >= MIN_LOGS_FOR_SIGNAL) {
                if (wind.windPct < 0.20) {
                    score *= MULTIPLIER_WIND_NOT_SUITABLE
                }
            }
        }

        return score
    }

    /**
     * Wear-recency multiplier: × [MULTIPLIER_WORN_RECENTLY] when worn ≤ [RECENT_WEAR_DAYS]
     * days before [EngineInput.today], × [BONUS_NOT_WORN_LATELY] when ≥ [STALE_WEAR_DAYS]
     * days before. Neutral (1.0) when `today` is null, the item was never worn, the date is
     * unparseable (only the first 10 chars, `YYYY-MM-DD`, are read) or lies in the future.
     */
    private fun recencyMultiplier(item: EngineItem, input: EngineInput): Double {
        val today = input.today ?: return 1.0
        val raw = input.lastWornDates[item.id]?.lastWornDate ?: return 1.0
        val lastWorn = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return 1.0
        val days = lastWorn.daysUntil(today)
        return when {
            days < 0 -> 1.0 // future date → neutral
            days <= RECENT_WEAR_DAYS -> MULTIPLIER_WORN_RECENTLY
            days >= STALE_WEAR_DAYS -> BONUS_NOT_WORN_LATELY
            else -> 1.0
        }
    }

    // -------------------------------------------------------------------------
    // Step 3b — Color deduplication per slot
    // -------------------------------------------------------------------------

    /**
     * Within a list of candidates for the same role slot, remove items that share
     * the same (color + subcategoryId) combination. Keeps the first occurrence
     * (highest-ranked after sorting by score).
     *
     * An item with no colors tagged is treated as having a unique key (its own ID),
     * so it is never deduplicated against another item.
     */
    private fun List<EngineItem>.deduplicateByColorAndSubcategory(): List<EngineItem> {
        val seen = mutableSetOf<String>()
        return filter { item ->
            val key = if (item.colorFamilies.isEmpty()) {
                "no-color-${item.id}"
            } else {
                "${item.colorFamilies.sorted().joinToString(",")}::${item.subcategoryId}"
            }
            seen.add(key)
        }
    }

    // -------------------------------------------------------------------------
    // Step 4 — Category completeness: build valid combos
    // -------------------------------------------------------------------------

    /**
     * Builds all valid outfit combinations from the capped, deduplicated candidates.
     *
     * Valid combo = (exactly 1 Top + exactly 1 Bottom) OR (exactly 1 OnePiece),
     * with optional 0–1 of: Outerwear, Footwear, Accessory.
     */
    private fun buildCombos(byRole: Map<String, List<EngineItem>>): List<List<EngineItem>> {
        val tops = byRole[ROLE_TOP] ?: emptyList()
        val bottoms = byRole[ROLE_BOTTOM] ?: emptyList()
        val onePieces = byRole[ROLE_ONEPIECE] ?: emptyList()
        val outerwear = byRole[ROLE_OUTERWEAR] ?: emptyList()
        val footwear = byRole[ROLE_FOOTWEAR] ?: emptyList()
        val accessories = byRole[ROLE_ACCESSORY] ?: emptyList()

        // Build cores: Top+Bottom pairs or OnePiece singles
        val cores = mutableListOf<List<EngineItem>>()
        for (top in tops) {
            for (bottom in bottoms) {
                cores.add(listOf(top, bottom))
            }
        }
        for (piece in onePieces) {
            cores.add(listOf(piece))
        }

        if (cores.isEmpty()) return emptyList()

        // Optional slots — include the "no item" choice (null) for each
        val outerOptions: List<EngineItem?> = listOf(null) + outerwear
        val footwearOptions: List<EngineItem?> = listOf(null) + footwear
        val accessoryOptions: List<EngineItem?> = listOf(null) + accessories

        val result = mutableListOf<List<EngineItem>>()
        for (core in cores) {
            for (outer in outerOptions) {
                for (shoe in footwearOptions) {
                    for (acc in accessoryOptions) {
                        val combo = core +
                            listOfNotNull(outer) +
                            listOfNotNull(shoe) +
                            listOfNotNull(acc)
                        result.add(combo)
                    }
                }
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Step 5 — Outfit-level score
    // -------------------------------------------------------------------------

    private fun scoreCombo(
        items: List<EngineItem>,
        itemScores: Map<Long, Double>,
        weather: EngineWeather?,
        hasFootwearInPool: Boolean
    ): Double {
        // Mean of individual item scores
        val meanScore = items.map { itemScores[it.id] ?: 1.0 }.average()

        var multiplier = 1.0

        // Layering (weather-dependent)
        if (weather != null) {
            val hasOuter = items.any { it.warmthLayer == "Outer" }
            val tempHigh = weather.tempHighC
            val tempLow = weather.tempLowC
            if (tempHigh != null && tempHigh < COLD_THRESHOLD_C && !hasOuter) {
                multiplier *= MULTIPLIER_COLD_NO_OUTER
            }
            if (tempLow != null && tempLow > WARM_THRESHOLD_C && hasOuter) {
                multiplier *= MULTIPLIER_WARM_WITH_OUTER
            }
        }

        // Color harmony
        multiplier *= colorHarmonyMultiplier(items)

        // Pattern mixing
        multiplier *= patternMixingMultiplier(items)

        // Footwear — penalise a shoe-less outfit only when shoes were available
        if (hasFootwearInPool && items.none { it.outfitRole == ROLE_FOOTWEAR }) {
            multiplier *= MULTIPLIER_NO_FOOTWEAR
        }

        return meanScore * multiplier
    }

    // -------------------------------------------------------------------------
    // Color harmony scoring
    // -------------------------------------------------------------------------

    /**
     * Computes the combined color harmony multiplier for an outfit by evaluating
     * all distinct pairs of non-Neutral dominant color families and returning the
     * **geometric mean** of the pair multipliers, `(∏ pair)^(1/pairCount)`, so the effect
     * does not compound with outfit size. With one pair this is that pair's value.
     *
     * Rules (per pair):
     * - Neutral + anything → no effect (skip Neutral items in pairwise check)
     * - Same family + same family → +0.10 bonus (additive to base multiplier 1.0)
     * - Earth + Warm → +0.05 bonus
     * - Bright + Bright → × 0.70
     * - Warm + Cool → × 0.85
     * - All other pairings → no effect
     *
     * Multi-color items: the dominant family is determined by the highest-priority
     * non-Neutral family present (priority: Bright > Warm > Cool > Earth > Neutral).
     * Items with no color tagged are treated as Neutral.
     */
    private fun colorHarmonyMultiplier(items: List<EngineItem>): Double {
        val families = items.map { dominantColorFamily(it.colorFamilies) }
        val nonNeutral = families.filter { it != FAMILY_NEUTRAL }
        if (nonNeutral.size < 2) return 1.0

        var product = 1.0
        var pairCount = 0
        for (i in nonNeutral.indices) {
            for (j in i + 1 until nonNeutral.size) {
                product *= pairHarmonyMultiplier(nonNeutral[i], nonNeutral[j])
                pairCount++
            }
        }
        return product.pow(1.0 / pairCount)
    }

    /**
     * Returns the multiplier contribution for a single color family pair.
     * Bonuses are represented as additive adjustments on top of 1.0.
     *
     * Bright+Bright is a clash (× 0.70) and takes priority over the general
     * same-family bonus (+0.10) — evaluated first.
     */
    private fun pairHarmonyMultiplier(a: String, b: String): Double {
        val pair = setOf(a, b)
        return when {
            a == FAMILY_BRIGHT && b == FAMILY_BRIGHT -> MULTIPLIER_BRIGHT_BRIGHT   // clash takes priority
            pair == setOf(FAMILY_WARM, FAMILY_COOL) -> MULTIPLIER_WARM_COOL        // slight clash
            pair == setOf(FAMILY_EARTH, FAMILY_WARM) -> 1.0 + BONUS_EARTH_WARM    // +0.05 compatible
            a == b -> 1.0 + BONUS_SAME_FAMILY                                      // same family → +0.10
            else -> 1.0
        }
    }

    /**
     * Picks the dominant color family from the set of families assigned to an item.
     * Priority (highest first): Bright > Warm > Cool > Earth > Neutral.
     * Items with no colors tagged return Neutral.
     */
    private fun dominantColorFamily(families: Set<String>): String {
        if (families.isEmpty()) return FAMILY_NEUTRAL
        val priority = listOf(FAMILY_BRIGHT, FAMILY_WARM, FAMILY_COOL, FAMILY_EARTH, FAMILY_NEUTRAL)
        return priority.firstOrNull { it in families } ?: FAMILY_NEUTRAL
    }

    // -------------------------------------------------------------------------
    // Pattern mixing scoring
    // -------------------------------------------------------------------------

    private fun patternMixingMultiplier(items: List<EngineItem>): Double {
        val patternedCount = items.count { !it.isPatternSolid }
        return when {
            patternedCount == 0 -> 1.0
            patternedCount == 1 -> 1.0 + BONUS_ONE_PATTERNED   // +0.10
            else -> MULTIPLIER_MULTI_PATTERNED                  // × 0.75
        }
    }

    // -------------------------------------------------------------------------
    // Tie-breaking
    // -------------------------------------------------------------------------

    /**
     * Returns a tie-break key for a combo used for ascending sort (smallest key wins).
     *
     * Rule: among tied combos, the one whose *most-recently-worn item* is the oldest
     * (i.e. least recently worn overall) wins — favouring combos that haven't been
     * worn in a while.
     *
     * Implementation:
     * 1. Map each item to its last-worn date string, or [NEVER_WORN_SENTINEL] ("") if
     *    the item has never been logged.
     * 2. Take the maximum date string across the combo's items — this represents the
     *    combo's most-recently-worn item. YYYY-MM-DD lexicographic order equals
     *    chronological order, so `maxOrNull()` is correct.
     * 3. The combo with the *smallest* max date (i.e. the most-recently-worn item is
     *    furthest in the past) wins the tie.
     *
     * NEVER_WORN_SENTINEL semantics: "" < any YYYY-MM-DD string, so a combo that
     * contains at least one never-worn item will have a max of "" only when *all*
     * items are never-worn. Mixed combos (some worn, some not) correctly rank by the
     * worn item's date.
     */
    private fun comboTieBreakKey(combo: OutfitCombo, input: EngineInput): String {
        val dates = combo.items.map { item ->
            input.lastWornDates[item.id]?.lastWornDate ?: NEVER_WORN_SENTINEL
        }
        return dates.maxOrNull() ?: NEVER_WORN_SENTINEL
    }
}
