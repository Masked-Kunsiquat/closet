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
 * (or fewer if the wardrobe cannot form that many valid combos). The page size defaults to
 * [TOP_N]; the ViewModel asks for 25 when AI curation is on.
 *
 * ### Pipeline (engine v2, #96: a per-core stochastic beam)
 * 1. Hard filter — redundant safety check: Active + Clean (DAO already filtered, but
 *    the engine applies it again as a defensive guard).
 * 2. Per-item suitability scoring — multiplicative multipliers from weather signals
 *    (signals with < 5 logs are skipped: sparse-data guard) and wear recency (only when
 *    [EngineInput.today] is set: worn ≤ 3 days ago × 0.90, ≥ 30 days ago × 1.05).
 * 3. Candidates — group by outfit role, order each role by item score (items already shown
 *    this session after unshown ones) and drop near-identical items: one per (color +
 *    subcategory) within a role. There is no per-role cap.
 * 4. Cores — every Top × Bottom pair and every OnePiece, each scored with the outfit formula
 *    (step 7) on its own items, without the situational terms still undecided (below).
 *    [selectTopK] picks [CORE_BEAM] of them; cores of already-shown (excluded) combos rank
 *    after unshown ones.
 * 5. Per-core extension — each picked core goes separately through the optional slots in the
 *    order outerwear → footwear → accessory, skipping roles with no candidates. Every slot
 *    offers "none" plus every candidate of that role; each partial outfit is scored with the
 *    same outfit formula on the items chosen so far, and [selectTopK] keeps [BEAM_PER_CORE]
 *    partials per core after each slot. The layering-vs-weather multiplier applies once
 *    outerwear is decided (from the start when the wardrobe has none), the missing-footwear
 *    penalty once footwear is decided, so a complete outfit gets exactly the full formula.
 *    Holding the beam per core keeps one dominant core from crowding the others out.
 * 6. Exclusion inside the search — at the last non-empty slot (or on the cores themselves
 *    when there is no optional slot), outfits whose [key] is in `exclude` (already shown this
 *    session) are dropped *before* the selection, so shown outfits never take a beam place.
 * 7. Outfit-level score — `meanItem × [W·H + (1 − W)·R] × layering × footwear`:
 *    - `R`, the taste rules: color harmony (the geometric mean over non-Neutral item pairs of
 *      the hue rule when both items have a chromatic hex of their dominant family — analogous
 *      ≤ 30° or complementary 150°–210° × 1.05, an 80°–100° clash × 0.90 — otherwise the
 *      color-family rule; same-family pairs use max(familyRule, hueTerm) so same-family
 *      cohesion keeps its bonus) × pattern
 *      mixing × the geometric mean over all item pairs of the formality term (× 0.85 when the
 *      two levels differ by 2 or more, 1.0 when either is unknown).
 *    - `H`, the habit: the geometric mean over all item pairs of the co-wear lift
 *      `clamp((c + 1) / (E + 1), 0.7, 1.3)`, `c` the weighted co-wear count and
 *      `E = nA·nB / N` its chance expectation ([EngineInput.coWear]). When direct wear evidence
 *      is sparse (n < 5.0), two-level backoff generalizes history:
 *      Level 1: cosine nearest neighbors (from 384-dim ONNX embeddings) contribute similarity-weighted
 *      lift discounted by [ALPHA_NEIGHBORS] (0.6).
 *      Level 2: aggregate subcategory-pair co-wears across the wardrobe contribute coarse baseline lift
 *      discounted by [BETA_SUBCATEGORY] (0.25).
 *    - `W`, the mean over all item pairs of `min(0.7, n / (n + 5))`, `n = max(c, E)` (or `n_eff`
 *      under backoff). With no history `W = 0` and the score equals the rules-only score exactly;
 *      with heavy history habit can outvote the rules, which keep at least 30%.
 *    - Layering for the weather and × 0.85 for a shoe-less outfit (when the wardrobe has
 *      footwear) are situational and stay outside the blend.
 * 8. Final pick — [selectTopK] orders all complete outfits; then diversity: take outfits
 *    greedily, skipping any whose core (Top + Bottom, or OnePiece) is already picked or was in
 *    an excluded combo, and fill the remaining slots from the skipped ones in order.
 * 9. Widening — when exclusion leaves fewer than `topN`, [CORE_BEAM] and [BEAM_PER_CORE]
 *    double, up to [MAX_WIDENED_CORE_BEAM] and [MAX_WIDENED_BEAM_PER_CORE].
 *
 * **Selection.** With no `jitter` [Random], every "select" is a strict top-k by score, ties
 * broken by the oldest last-worn date (then build order), so the result is deterministic.
 * With one, it is seeded Gumbel-top-k at [TEMPERATURE]: sampling without replacement in
 * proportion to `score^(1/T)`, so runner-ups get a chance and weak outfits almost never do.
 * [OutfitCombo.score] is always the un-noised outfit score.
 */
class OutfitRecommendationEngine {

    companion object {
        // --- Temperatures ---
        internal const val COLD_THRESHOLD_C = 5.0
        internal const val WARM_THRESHOLD_C = 20.0

        // --- Per-item multipliers ---
        private const val MULTIPLIER_TEMP_OUT_OF_RANGE = 0.55
        private const val MULTIPLIER_RAIN_NOT_SUITABLE = 0.60
        private const val MULTIPLIER_WIND_NOT_SUITABLE = 0.70

        // --- Outfit-level multipliers ---
        internal const val MULTIPLIER_COLD_NO_OUTER = 0.50
        internal const val MULTIPLIER_WARM_WITH_OUTER = 0.75

        // --- Color harmony ---
        internal const val BONUS_SAME_FAMILY = 0.10
        internal const val BONUS_EARTH_WARM = 0.05
        internal const val MULTIPLIER_BRIGHT_BRIGHT = 0.70
        internal const val MULTIPLIER_WARM_COOL = 0.85

        // --- Pattern mixing ---
        internal const val BONUS_ONE_PATTERNED = 0.10
        internal const val MULTIPLIER_MULTI_PATTERNED = 0.75

        // --- Footwear ---
        internal const val MULTIPLIER_NO_FOOTWEAR = 0.85

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

        // --- History generalization / embedding backoff (engine v2, Delivery 3, #96) ---
        /** Discount factor for Level 1 neighbor co-wear evidence. */
        internal const val ALPHA_NEIGHBORS = 0.6
        /** Discount factor for Level 2 subcategory co-wear evidence. */
        internal const val BETA_SUBCATEGORY = 0.25
        /** Minimum cosine similarity for an item to count as a neighbor. */
        internal const val MIN_NEIGHBOR_SIMILARITY = 0.75
        /** Maximum number of nearest neighbors per item. */
        internal const val TOP_K_NEIGHBORS = 5

        // --- Wear recency ---
        private const val RECENT_WEAR_DAYS = 3
        private const val STALE_WEAR_DAYS = 30
        private const val MULTIPLIER_WORN_RECENTLY = 0.90
        private const val BONUS_NOT_WORN_LATELY = 1.05

        // --- Sparse data guard ---
        private const val MIN_LOGS_FOR_SIGNAL = 5

        // --- Top N result (the default page size; the ViewModel shows this many) ---
        internal const val TOP_N = 3

        // --- Beam assembly (engine v2, #96) ---
        /** Cores picked for extension. */
        internal const val CORE_BEAM = 30
        /** Partial outfits kept per core after each optional slot. */
        internal const val BEAM_PER_CORE = 4
        /**
         * Gumbel-top-k temperature: selection weight ∝ `score^(1/T)`, so at 0.01 a 1% better
         * outfit is ~2.7× likelier and a 10% better one ~20,000× — near-ties rotate between
         * runs, clear winners stay. Tuned down from the spec's starting 0.15 by the session
         * tests (echo-chamber guard, 1,000s of seeds): at 0.15 a co-worn pair 21% above the
         * rest led the first page only ~34% of the time and missed it ~24%, and an unseeded
         * Regenerate skipped every core without the dominant items ~6% of the time at 0.05 and
         * ~0.2% at 0.02; at 0.01, 0 of 120,000.
         */
        internal const val TEMPERATURE = 0.01
        /** Bounds for the beams when exclusion leaves fewer than a page (each step doubles). */
        internal const val MAX_WIDENED_CORE_BEAM = 120
        internal const val MAX_WIDENED_BEAM_PER_CORE = 12

        // --- Outfit roles ---
        internal const val ROLE_TOP = "Top"
        internal const val ROLE_BOTTOM = "Bottom"
        internal const val ROLE_ONEPIECE = "OnePiece"
        internal const val ROLE_OUTERWEAR = "Outerwear"
        internal const val ROLE_FOOTWEAR = "Footwear"
        internal const val ROLE_ACCESSORY = "Accessory"
        internal val CORE_ROLES = setOf(ROLE_TOP, ROLE_BOTTOM, ROLE_ONEPIECE)

        // --- Color families ---
        internal const val FAMILY_NEUTRAL = "Neutral"
        internal const val FAMILY_EARTH = "Earth"
        internal const val FAMILY_COOL = "Cool"
        internal const val FAMILY_WARM = "Warm"
        internal const val FAMILY_BRIGHT = "Bright"

        // Sentinel for "never worn" tie-breaking — sorts before any real date.
        private const val NEVER_WORN_SENTINEL = ""
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Run the full recommendation pipeline and return the top-N combos.
     *
     * @param input   Pre-fetched engine input (no I/O performed inside this call).
     * @param topN    Maximum number of combos to return. Defaults to [TOP_N] (3).
     *                Pass a larger value (e.g. 25) when AI is enabled so the
     *                coherence scorer has a wider pool to curate from.
     * @param exclude Combo keys ([OutfitCombo.key]) never to return — the combos already shown
     *                in the current session. Empty by default. When it leaves fewer than [topN]
     *                outfits, the beams widen (bounded) before giving up.
     * @param jitter  Optional randomness for every selection step: seeded Gumbel-top-k at
     *                [TEMPERATURE] (see the class KDoc). [OutfitCombo.score] keeps the un-noised
     *                value. Null (default) gives the deterministic strict top-k.
     * @return Up to [topN] [OutfitCombo]s, best first, preferring distinct cores.
     *         Empty if the wardrobe cannot form any valid, non-excluded outfit combination
     *         within the widened beams.
     */
    fun recommend(
        input: EngineInput,
        topN: Int = TOP_N,
        exclude: Set<Set<Long>> = emptySet(),
        jitter: Random? = null,
    ): List<OutfitCombo> {
        require(topN >= 0) { "topN must be >= 0, was $topN" }
        var coreBeam = CORE_BEAM
        var beamPerCore = BEAM_PER_CORE
        var result = assemble(input, topN, exclude, jitter, coreBeam, beamPerCore)
        // Widening: expands the beams if the result is short of topN (e.g. from exclusions or high topN).
        while (
            result.size < topN &&
            (coreBeam < MAX_WIDENED_CORE_BEAM || beamPerCore < MAX_WIDENED_BEAM_PER_CORE)
        ) {
            coreBeam = minOf(coreBeam * 2, MAX_WIDENED_CORE_BEAM)
            beamPerCore = minOf(beamPerCore * 2, MAX_WIDENED_BEAM_PER_CORE)
            result = assemble(input, topN, exclude, jitter, coreBeam, beamPerCore)
        }
        return result
    }

    /**
     * Steps 1–8 at fixed beam sizes (no widening): prepare the candidates, pick [coreBeam] cores,
     * extend each one separately through the optional slots keeping [beamPerCore] partials,
     * then pick the page. Internal so tests can run the search without widening.
     */
    internal fun assemble(
        input: EngineInput,
        topN: Int,
        exclude: Set<Set<Long>>,
        random: Random?,
        coreBeam: Int,
        beamPerCore: Int,
    ): List<OutfitCombo> {
        if (topN == 0) return emptyList()
        // 1. Hard filter — redundant safety guard on top of DAO filter
        val safeItems = input.candidates.filter { isActiveAndClean(it) }
        if (safeItems.isEmpty()) return emptyList()

        // 2. Score each item individually
        val itemScores: Map<Long, Double> = safeItems.associateBy(
            keySelector = { it.id },
            valueTransform = { item -> itemScore(item, input) }
        )

        // 3. Candidates per role: score order, shown items after unshown (so the dedupe keeps
        //    the unshown twin), near-identical items dropped. Both sorts are stable.
        val shownItemIds: Set<Long> = exclude.flatMapTo(mutableSetOf()) { it }
        val byRole: Map<String, List<EngineItem>> = safeItems
            .groupBy { it.outfitRole ?: "Other" }
            .mapValues { (_, items) ->
                items
                    .sortedByDescending { itemScores[it.id] ?: 1.0 }
                    .let { sorted ->
                        if (shownItemIds.isEmpty()) sorted else sorted.sortedBy { it.id in shownItemIds }
                    }
                    .deduplicateByColorAndSubcategory()
            }
        // Only roles with candidates are slots, so the last selection (where exclusion applies)
        // is the last slot that really decides something.
        val optionalSlots = listOf(ROLE_OUTERWEAR, ROLE_FOOTWEAR, ROLE_ACCESSORY)
            .filter { byRole[it].orEmpty().isNotEmpty() }
        val hasFootwear = ROLE_FOOTWEAR in optionalSlots
        // Situational terms by role: layering once Outerwear is decided (or there is none to
        // decide), the shoe-less penalty once Footwear is decided. With every slot decided this
        // is exactly the full outfit formula.
        val score = { items: List<EngineItem>, decided: Set<String> ->
            scoreCombo(
                items, itemScores,
                weather = if (ROLE_OUTERWEAR in decided || ROLE_OUTERWEAR !in optionalSlots) input.weather else null,
                hasFootwearInPool = hasFootwear && ROLE_FOOTWEAR in decided,
                coWear = input.coWear,
                itemNeighbors = input.itemNeighbors,
                subcategoryCoWear = input.subcategoryCoWear,
                tasteWeights = input.tasteWeights,
            )
        }
        val tieBreak = compareBy<Scored> { tieBreakKey(it.items, input) }
        fun select(candidates: List<Scored>, k: Int): List<Scored> =
            selectTopK(candidates, { it.score }, k, random, TEMPERATURE, tieBreak)

        // 4. Cores: Top × Bottom pairs, then OnePieces; shown cores rank after unshown ones.
        //    With no optional slot a core is a complete outfit, so exclusion applies here.
        val excludeCores = optionalSlots.isEmpty() && exclude.isNotEmpty()
        val cores = mutableListOf<Scored>()
        for (top in byRole[ROLE_TOP].orEmpty()) {
            for (bottom in byRole[ROLE_BOTTOM].orEmpty()) {
                val items = listOf(top, bottom)
                if (excludeCores && items.idSet() in exclude) continue
                cores.add(Scored(items, score(items, emptySet())))
            }
        }
        for (piece in byRole[ROLE_ONEPIECE].orEmpty()) {
            val items = listOf(piece)
            if (excludeCores && items.idSet() in exclude) continue
            cores.add(Scored(items, score(items, emptySet())))
        }
        if (cores.isEmpty()) return emptyList()
        val shownCores = excludedCores(exclude, input)
        val (unshownCores, seenCores) = cores.partition { it.items.idSet() !in shownCores }
        val pickedCores = select(unshownCores, coreBeam).let { picked ->
            if (picked.size >= coreBeam) picked else picked + select(seenCores, coreBeam - picked.size)
        }

        // 5–6. Extend each core on its own beam through the non-empty optional slots; drop
        //      excluded keys before the last slot's selection.
        val complete = mutableListOf<Scored>()
        for (core in pickedCores) {
            var partials = listOf(core)
            optionalSlots.forEachIndexed { index, role ->
                val options: List<EngineItem?> = listOf(null) + byRole[role].orEmpty()
                val isLast = index == optionalSlots.lastIndex
                val decided = optionalSlots.subList(0, index + 1).toSet()
                val extended = ArrayList<Scored>(partials.size * options.size)
                for (partial in partials) {
                    for (option in options) {
                        val items = if (option == null) partial.items else partial.items + option
                        if (isLast && exclude.isNotEmpty() && items.idSet() in exclude) continue
                        extended.add(Scored(items, score(items, decided)))
                    }
                }
                partials = select(extended, beamPerCore)
            }
            complete.addAll(partials)
        }
        if (complete.isEmpty()) return emptyList()

        // 8. Final pick: order every complete outfit, then diversity by core + fill.
        val ordered = select(complete, complete.size).map { OutfitCombo(items = it.items, score = it.score) }
        return selectDiverse(ordered, topN, shownCores = shownCores)
    }

    /** A (partial) outfit and its un-noised outfit score. */
    private class Scored(val items: List<EngineItem>, val score: Double)

    private fun List<EngineItem>.idSet(): Set<Long> = mapTo(HashSet(size)) { it.id }

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
    // Step 3 — Color deduplication per role
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
    // Step 7 — Outfit-level score
    // -------------------------------------------------------------------------

    /**
     * `meanItem × [W·H + (1 − W)·R] × layering × footwear`, over all item pairs of the combo:
     * `R` = color harmony × pattern mixing × the geometric mean of [PairScorer.formalityTerm],
     * `H` = the geometric mean of the pairs' history lifts, `W` = the mean of their weights.
     *
     * When [tasteWeights] is not [com.closet.features.recommendations.engine.learning.TasteWeights.DEFAULT],
     * evaluates the personalized log-linear conditional logit utility $\exp(\beta^\top x)$ over the
     * 8 features extracted by [com.closet.features.recommendations.engine.learning.OutfitFeatureExtractor].
     */
    internal fun scoreCombo(
        items: List<EngineItem>,
        itemScores: Map<Long, Double>,
        weather: EngineWeather?,
        hasFootwearInPool: Boolean,
        coWear: CoWearStats,
        itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
        subcategoryCoWear: CoWearStats = CoWearStats.EMPTY,
        tasteWeights: com.closet.features.recommendations.engine.learning.TasteWeights =
            com.closet.features.recommendations.engine.learning.TasteWeights.DEFAULT,
    ): Double {
        if (!tasteWeights.isDefault) {
            val features = com.closet.features.recommendations.engine.learning.OutfitFeatureExtractor.extract(
                items = items,
                itemScores = itemScores,
                weather = weather,
                hasFootwearInPool = hasFootwearInPool,
                coWear = coWear,
                itemNeighbors = itemNeighbors,
                subcategoryCoWear = subcategoryCoWear,
            )
            return exp(tasteWeights.dot(features))
        }

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
                val history = PairScorer.history(
                    items[i],
                    items[j],
                    coWear,
                    itemNeighbors,
                    subcategoryCoWear
                )
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
     * hex; otherwise the family rules below. For same-family pairs, `max(familyRule, hueTerm)`
     * applies so that same-family cohesion retains its bonus.
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
                val hue = PairScorer.hueTerm(a, b)
                val familyMultiplier = pairHarmonyMultiplier(familyA, familyB)
                val pairMultiplier = if (hue != null && familyA == familyB) {
                    maxOf(familyMultiplier, hue)
                } else {
                    hue ?: familyMultiplier
                }
                product *= pairMultiplier
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
     * Returns a tie-break key for a (partial) outfit used for ascending sort (smallest key wins).
     *
     * Rule: among tied combos, the one whose *most-recently-worn item* is the oldest
     * (i.e. least recently worn overall) wins — favouring combos that haven't been
     * worn in a while.
     *
     * It orders strict (no-[Random]) selections. Exact score ties are common (at cold start
     * most outfits score the same); in a seeded Gumbel-top-k selection their draws break such
     * ties uniformly at random instead, and the recency multiplier covers wear history there.
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
    private fun tieBreakKey(items: List<EngineItem>, input: EngineInput): String {
        val dates = items.map { item ->
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
