package com.closet.features.recommendations.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.exp
import kotlin.math.ln
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
 * 3. Per-category cap — keep top 1–2 scored candidates per outfit-role slot. When `exclude`
 *    is non-empty, items that appear in any excluded combo are ranked after the unshown ones
 *    (ranking only — their scores are unchanged), so Regenerate rotates new clothes into the
 *    capped pool instead of re-pairing the same few.
 * 4. Color deduplication — one item per (color+subcategory) combo within each slot.
 * 5. Category completeness — build valid combos: (Top + Bottom) OR (OnePiece),
 *    optional Outerwear / Footwear / Accessory.
 * 6. Outfit-level scoring — `meanItem × [W·H + (1 − W)·R] × layering × footwear`:
 *    - `R`, the taste rules: color harmony (the geometric mean over non-Neutral item pairs of
 *      the hue rule when both items have a chromatic hex of their dominant family — analogous
 *      ≤ 30° or complementary 150°–210° × 1.05, an 80°–100° clash × 0.90 — otherwise the
 *      color-family rule) × pattern
 *      mixing × the geometric mean over all item pairs of the formality term (× 0.85 when the
 *      two levels differ by 2 or more, 1.0 when either is unknown).
 *    - `H`, the habit: the geometric mean over all item pairs of the co-wear lift
 *      `clamp((c + 1) / (E + 1), 0.7, 1.3)`, `c` the weighted co-wear count and
 *      `E = nA·nB / N` its chance expectation ([EngineInput.coWear]).
 *    - `W`, the mean over all item pairs of `min(0.7, n / (n + 5))`, `n = max(c, E)`. With no
 *      history `W = 0` and the score equals the rules-only score exactly; with heavy history
 *      habit can outvote the rules, which keep at least 30%.
 *    - Layering for the weather and × 0.85 for a shoe-less outfit (when the capped pool has
 *      footwear) are situational and stay outside the blend.
 * 7. Exclusion — drop combos whose [key] is in `exclude` (already shown this session).
 * 8. Sort + tie-break — descending score (or score × (1 ± 0.03) when a `jitter` [Random]
 *    is given; [OutfitCombo.score] stays un-jittered); ties broken by oldest last-worn date.
 *    With jitter, exact ties are rare, so the last-worn tie-break effectively applies only
 *    to un-jittered runs; the recency multiplier covers wear history either way.
 * 9. Diversity — take combos greedily, skipping any whose core (Top + Bottom, or
 *    OnePiece) is already picked or was in an excluded combo; fill remaining slots from the
 *    skipped combos in order.
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

        // --- Pair scoring (engine v2, #96; used by PairScorer) ---
        /** Shrinkage: a pair's history weight is `n / (n + K)`. */
        internal const val K = 5.0
        /** Cap on a pair's history weight, so the taste rules always keep 30%. */
        internal const val W_MAX = 0.7
        /** Bounds of a pair's history lift. */
        internal const val PAIR_MIN = 0.7
        internal const val PAIR_MAX = 1.3
        /** Applied per item pair whose formality levels differ by 2 or more. */
        internal const val FORMALITY_GAP_PENALTY = 0.85

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

        // --- Top N result (the default page size; the ViewModel shows this many) ---
        internal const val TOP_N = 3

        // --- Exclusion widening: the largest per-slot cap tried when exclusion leaves less
        //     than a page. 5 per slot builds at most 30 cores × 6³ optional choices = 6,480 combos.
        private const val MAX_WIDENED_CANDIDATES_PER_SLOT = 5

        // --- Outfit roles ---
        private const val ROLE_TOP = "Top"
        private const val ROLE_BOTTOM = "Bottom"
        private const val ROLE_ONEPIECE = "OnePiece"
        private const val ROLE_OUTERWEAR = "Outerwear"
        private const val ROLE_FOOTWEAR = "Footwear"
        private const val ROLE_ACCESSORY = "Accessory"
        internal val CORE_ROLES = setOf(ROLE_TOP, ROLE_BOTTOM, ROLE_ONEPIECE)

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
     *                          Increase to 3 when AI is enabled to broaden variety. With a
     *                          non-empty [exclude] that leaves fewer than [topN] combos, the cap
     *                          widens one step at a time up to [MAX_WIDENED_CANDIDATES_PER_SLOT].
     * @param exclude           Combo keys ([OutfitCombo.key]) to drop before ranking — the
     *                          combos already shown in the current session. Empty by default.
     * @param jitter            Optional randomness for the ranking only: each combo's sort key
     *                          becomes `score × (1 + u)`, `u` uniform in [-0.03, 0.03].
     *                          [OutfitCombo.score] keeps the un-jittered value. Null (default)
     *                          gives the deterministic order.
     * @return Up to [topN] [OutfitCombo]s ranked highest-first, preferring distinct cores.
     *         Empty if the wardrobe cannot form any valid, non-excluded outfit combination
     *         within the widened cap.
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
            valueTransform = { item -> itemScore(item, input) }
        )

        // 3–6. Cap per slot, build and score combos, drop the excluded ones. When exclusion
        //      leaves less than a page, widen the per-slot cap (bounded) so unseen combos that
        //      need lower-ranked items are still reachable before the caller restarts the cycle.
        val shownItemIds: Set<Long> = exclude.flatMapTo(mutableSetOf()) { it }
        val byRole = safeItems.groupBy { it.outfitRole ?: "Other" }
        val widestSlot = byRole.values.maxOf { it.size }
        val maxCap = if (exclude.isEmpty()) candidatesPerSlot else maxOf(candidatesPerSlot, MAX_WIDENED_CANDIDATES_PER_SLOT)
        var cap = candidatesPerSlot
        var remaining = unexcludedCombos(byRole, itemScores, shownItemIds, cap, exclude, input)
        while (remaining.size < topN && cap < maxCap && cap < widestSlot) {
            cap++
            remaining = unexcludedCombos(byRole, itemScores, shownItemIds, cap, exclude, input)
        }
        if (remaining.isEmpty()) return emptyList()

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

        // 8. Diversity: distinct cores first (cores already shown count as taken), then fill
        //    from the skipped combos
        return selectDiverse(sorted, topN, shownCores = excludedCores(exclude, input))
    }

    /**
     * Steps 3–6 at one per-slot [cap]: group by role and cap each slot (items already shown,
     * in [shownItemIds], rank after unshown ones within their slot — ranking only, scores
     * unchanged; both sorts are stable, so with nothing shown the order is exactly
     * score-descending), build every valid combo, score it, and drop the [exclude]d keys.
     */
    private fun unexcludedCombos(
        byRole: Map<String, List<EngineItem>>,
        itemScores: Map<Long, Double>,
        shownItemIds: Set<Long>,
        cap: Int,
        exclude: Set<Set<Long>>,
        input: EngineInput,
    ): List<OutfitCombo> {
        val cappedByRole: Map<String, List<EngineItem>> = byRole.mapValues { (_, items) ->
            items
                .sortedByDescending { itemScores[it.id] ?: 1.0 }
                .let { sorted -> if (shownItemIds.isEmpty()) sorted else sorted.sortedBy { it.id in shownItemIds } }
                .deduplicateByColorAndSubcategory()
                .take(cap)
        }
        val combos = buildCombos(cappedByRole)
        val hasFootwearInPool = cappedByRole[ROLE_FOOTWEAR].orEmpty().isNotEmpty()
        return combos
            .map { comboItems ->
                OutfitCombo(
                    items = comboItems,
                    score = scoreCombo(comboItems, itemScores, input.weather, hasFootwearInPool, input.coWear),
                )
            }
            .let { scored -> if (exclude.isEmpty()) scored else scored.filter { it.key() !in exclude } }
    }

    /** The cores of the excluded combos, resolving each key's item IDs to their input roles. */
    private fun excludedCores(exclude: Set<Set<Long>>, input: EngineInput): Set<Set<Long>> {
        if (exclude.isEmpty()) return emptySet()
        val roleById: Map<Long, String?> = input.candidates.associate { it.id to it.outfitRole }
        return exclude
            .map { key -> key.filter { roleById[it] in CORE_ROLES }.toSet() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    // -------------------------------------------------------------------------
    // Diversity selection
    // -------------------------------------------------------------------------

    /**
     * Greedily takes combos from [sorted] whose core (Top + Bottom IDs, or the OnePiece ID)
     * has not been picked yet and is not in [shownCores] (cores of combos already shown this
     * session), up to [topN]. If that yields fewer than [topN], the remaining slots are filled
     * from the skipped combos in their sorted order, so no valid outfit is dropped just for
     * sharing a core.
     */
    private fun selectDiverse(
        sorted: List<OutfitCombo>,
        topN: Int,
        shownCores: Set<Set<Long>> = emptySet(),
    ): List<OutfitCombo> {
        val picked = mutableListOf<OutfitCombo>()
        val skipped = mutableListOf<OutfitCombo>()
        val seenCores = shownCores.toMutableSet()
        for (combo in sorted) {
            if (picked.size == topN) break
            if (seenCores.add(combo.core())) picked.add(combo) else skipped.add(combo)
        }
        if (picked.size < topN) {
            picked.addAll(skipped.take(topN - picked.size))
        }
        return picked
    }


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

    /**
     * Per-item suitability score: the wear-recency multiplier times the weather-signal
     * multipliers. Internal so the ViewModel's AI hint scores use exactly the engine's logic.
     */
    internal fun itemScore(item: EngineItem, input: EngineInput): Double {
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
    // Step 6 — Outfit-level score
    // -------------------------------------------------------------------------

    /**
     * `meanItem × [W·H + (1 − W)·R] × layering × footwear`, over all item pairs of the combo:
     * `R` = color harmony × pattern mixing × the geometric mean of [PairScorer.formalityTerm],
     * `H` = the geometric mean of the pairs' history lifts, `W` = the mean of their weights.
     */
    private fun scoreCombo(
        items: List<EngineItem>,
        itemScores: Map<Long, Double>,
        weather: EngineWeather?,
        hasFootwearInPool: Boolean,
        coWear: CoWearStats,
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

        // Taste: the rules R (color harmony × pattern mixing × formality), blended with the
        // co-wear habit H by its weight W. With no history W = 0 and this is exactly R, applied
        // term by term as before pair scoring existed (cold-start parity).
        var habitWeightSum = 0.0
        var logLiftSum = 0.0
        var formalityProduct = 1.0
        var pairCount = 0
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val history = PairScorer.history(items[i].id, items[j].id, coWear)
                habitWeightSum += history.weight
                logLiftSum += ln(history.lift)
                formalityProduct *= PairScorer.formalityTerm(items[i], items[j])
                pairCount++
            }
        }
        val colorHarmony = colorHarmonyMultiplier(items)
        val patternMixing = patternMixingMultiplier(items)
        val formality = if (pairCount == 0) 1.0 else formalityProduct.pow(1.0 / pairCount)
        val habitWeight = if (pairCount == 0) 0.0 else habitWeightSum / pairCount
        if (habitWeight == 0.0) {
            multiplier *= colorHarmony
            multiplier *= patternMixing
            multiplier *= formality
        } else {
            val rules = colorHarmony * patternMixing * formality
            val habit = exp(logLiftSum / pairCount)
            multiplier *= habitWeight * habit + (1.0 - habitWeight) * rules
        }

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
     * all distinct pairs of items whose dominant color family is not Neutral and returning the
     * **geometric mean** of the pair multipliers, `(∏ pair)^(1/pairCount)`, so the effect
     * does not compound with outfit size. With one pair this is that pair's value.
     *
     * Per pair, the hue rule ([PairScorer.hueTerm]) applies when both items have a chromatic
     * hex; otherwise the family rules below.
     *
     * Family rules (per pair):
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
        val nonNeutral = items
            .map { it to dominantColorFamily(it.colorFamilies) }
            .filter { (_, family) -> family != FAMILY_NEUTRAL }
        if (nonNeutral.size < 2) return 1.0

        var product = 1.0
        var pairCount = 0
        for (i in nonNeutral.indices) {
            for (j in i + 1 until nonNeutral.size) {
                val (a, familyA) = nonNeutral[i]
                val (b, familyB) = nonNeutral[j]
                product *= PairScorer.hueTerm(a, b) ?: pairHarmonyMultiplier(familyA, familyB)
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
    private fun dominantColorFamily(families: Set<String>): String = dominantFamilyOf(families)

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
     * With a `jitter` [Random], exact score ties are rare, so this tie-break effectively
     * applies only to un-jittered runs; the recency multiplier covers wear history then.
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

private val FAMILY_PRIORITY = listOf("Bright", "Warm", "Cool", "Earth", "Neutral")

/**
 * The dominant color family of an item: the highest-priority family present
 * (Bright > Warm > Cool > Earth > Neutral), Neutral when none is tagged. Shared by the family
 * harmony rules and [PairScorer.hueTerm], so both sides judge the same color.
 */
internal fun dominantFamilyOf(families: Set<String>): String =
    FAMILY_PRIORITY.firstOrNull { it in families } ?: "Neutral"

/** The combo's core: its Top + Bottom IDs, or its OnePiece ID. Two combos with the same core
 *  differ only in outerwear, footwear or accessories. */
internal fun OutfitCombo.core(): Set<Long> =
    items
        .filter { it.outfitRole in OutfitRecommendationEngine.CORE_ROLES }
        .map { it.id }
        .toSet()
