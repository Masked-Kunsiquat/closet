package com.closet.features.recommendations.engine.learning

import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.ItemNeighbor
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.colorDedupKey
import kotlinx.datetime.LocalDate

/**
 * One historical wear log entry used to construct training choice strata.
 *
 * @property logId   Unique wear log primary key (for deterministic ordering on same-day logs).
 * @property date    Calendar wear date.
 * @property itemIds Snapshot clothing item IDs worn in this log.
 * @property weather Optional weather conditions recorded on the log.
 */
data class HistoricalWearLog(
    val logId: Long,
    val date: LocalDate,
    val itemIds: List<Long>,
    val weather: EngineWeather? = null,
)

/**
 * Reconstructs historical choice strata ($d_1 \dots d_N$) from a user's wear log history
 * while enforcing strict temporal causality (`log.date < d`) and core-level IIA deduplication.
 *
 * ### Core-Level IIA Mitigation
 * For a wear log on date $d$:
 * - **Case ($y = 1$):** The 10-dimensional feature vector of the actual worn outfit.
 * - **Controls ($y = 0$):** Competing cores (Top × Bottom or OnePiece) available on date $d$,
 *   excluding the worn core and any other core worn in another log on the same date $d$.
 *   Each competing core is represented by its **single highest-default-scoring completion**
 *   (best optional outerwear, footwear, and accessory combination under [TasteWeights.DEFAULT]).
 */
internal object HistoricalStratumBuilder {

    /** Maximum number of competing control cores kept per stratum (matches engine [OutfitRecommendationEngine.CORE_BEAM]). */
    internal const val MAX_CONTROLS_PER_STRATUM = OutfitRecommendationEngine.CORE_BEAM

    /** Maximum number of recent choice strata constructed for training (older logs still feed causal history). */
    internal const val MAX_TRAINING_STRATA = 120

    private const val MAX_CORE_ROLE_CANDIDATES = 20
    private const val MAX_OPTIONAL_ROLE_CANDIDATES = 6
    private const val BEAM_PER_CORE_COMPLETION = 1

    /**
     * Builds the chronological list of [ChoiceStratum] from [items] and [logs].
     *
     * @param items          All active candidate clothing items in the wardrobe.
     * @param logs           Historical wear logs (in any order; sorted internally by `(date, logId)`).
     * @param createdDates   Optional map from item ID to its `createdAt` calendar date.
     *                       An item is considered available on date $d$ if
     *                       `min(createdDates[id], firstWornDate[id]) <= d`.
     *                       Items absent from [createdDates] are treated as always available.
     * @param itemNeighbors  Optional pre-computed cosine neighbor map for Level 1 history backoff.
     * @param engine         Engine instance used for per-item suitability scoring and default completion scoring.
     * @param maxControls    Maximum number of competing control cores per stratum.
     */
    fun buildStrata(
        items: List<EngineItem>,
        logs: List<HistoricalWearLog>,
        createdDates: Map<Long, LocalDate> = emptyMap(),
        itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
        engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
        maxControls: Int = MAX_CONTROLS_PER_STRATUM,
    ): List<ChoiceStratum> {
        if (items.isEmpty() || logs.isEmpty()) return emptyList()

        val sortedLogs = logs.sortedWith(compareBy<HistoricalWearLog> { it.date }.thenBy { it.logId })
        val itemById = items.associateBy { it.id }

        // Compute effective availability date per item: min(createdDate, firstWornDate).
        val firstWornDate = HashMap<Long, LocalDate>()
        for (log in sortedLogs) {
            for (id in log.itemIds) {
                val prev = firstWornDate[id]
                if (prev == null || log.date < prev) {
                    firstWornDate[id] = log.date
                }
            }
        }
        val effectiveStartDate: Map<Long, LocalDate> = items.associate { item ->
            val created = createdDates[item.id]
            val firstWorn = firstWornDate[item.id]
            val effective = when {
                created != null && firstWorn != null -> minOf(created, firstWorn)
                created != null -> created
                else -> LocalDate(1970, 1, 1)
            }
            item.id to effective
        }

        // Only construct strata for the most recent MAX_TRAINING_STRATA logs; earlier logs still
        // accumulate into priorLogs so co-wear lift and recency reflect full causal history.
        val stratumStartIndex = (sortedLogs.size - MAX_TRAINING_STRATA).coerceAtLeast(0)
        val activeLogIds: Set<Long> = sortedLogs.subList(stratumStartIndex, sortedLogs.size)
            .mapTo(HashSet()) { it.logId }

        // Group logs by date so we can identify same-day worn cores and advance prior history strictly by date < d
        val logsByDate = sortedLogs.groupBy { it.date }
        val priorLogs = ArrayList<HistoricalWearLog>(sortedLogs.size)
        val strata = ArrayList<ChoiceStratum>(minOf(sortedLogs.size, MAX_TRAINING_STRATA))

        for ((date, dayLogs) in logsByDate) {
            if (dayLogs.none { it.logId in activeLogIds }) {
                priorLogs.addAll(dayLogs)
                continue
            }

            val availableItems = items.filter { item ->
                (effectiveStartDate[item.id] ?: LocalDate(1970, 1, 1)) <= date
            }
            if (availableItems.isEmpty()) {
                priorLogs.addAll(dayLogs)
                continue
            }

            val allDayCoreSets: Set<Set<Long>> = dayLogs.mapNotNullTo(HashSet()) { log ->
                val presentItems = log.itemIds.distinct().mapNotNull { itemById[it] }
                extractValidCore(presentItems)
            }

            val dayCores: List<Triple<HistoricalWearLog, List<EngineItem>, Set<Long>>> = dayLogs.mapNotNull { log ->
                val distinctIds = log.itemIds.distinct()
                val wornItems = distinctIds.mapNotNull { itemById[it] }
                if (wornItems.size != distinctIds.size) return@mapNotNull null
                val core = extractValidCore(wornItems) ?: return@mapNotNull null
                Triple(log, wornItems, core)
            }

            if (dayCores.isNotEmpty()) {
                val historyStats = buildPriorStats(priorLogs, itemById)

                for ((log, wornItems, wornCore) in dayCores) {
                    if (log.logId !in activeLogIds) continue
                    val stratum = buildSingleStratum(
                        logId = log.logId,
                        date = date,
                        wornItems = wornItems,
                        wornCore = wornCore,
                        sameDayWornCores = allDayCoreSets,
                        availableItems = availableItems,
                        weather = log.weather,
                        historyStats = historyStats,
                        itemNeighbors = itemNeighbors,
                        engine = engine,
                        maxControls = maxControls,
                    )
                    if (stratum != null) {
                        strata.add(stratum)
                    }
                }
            }

            priorLogs.addAll(dayLogs)
        }

        return strata
    }

    // -------------------------------------------------------------------------
    // Single stratum reconstruction
    // -------------------------------------------------------------------------

    private fun buildSingleStratum(
        logId: Long,
        date: LocalDate,
        wornItems: List<EngineItem>,
        wornCore: Set<Long>,
        sameDayWornCores: Set<Set<Long>>,
        availableItems: List<EngineItem>,
        weather: EngineWeather?,
        historyStats: PriorHistoryStats,
        itemNeighbors: Map<Long, List<ItemNeighbor>>,
        engine: OutfitRecommendationEngine,
        maxControls: Int,
    ): ChoiceStratum? {
        val input = EngineInput(
            candidates = availableItems,
            tempPercentiles = historyStats.tempPercentiles,
            rainSuitability = historyStats.rainSuitability,
            windSuitability = historyStats.windSuitability,
            lastWornDates = historyStats.lastWornDates,
            weather = weather,
            today = date,
            coWear = historyStats.coWear,
            itemNeighbors = itemNeighbors,
            subcategoryCoWear = historyStats.subcategoryCoWear,
            tasteWeights = TasteWeights.DEFAULT,
        )

        val itemScores: Map<Long, Double> = availableItems.associate { item ->
            item.id to engine.itemScore(item, input)
        }

        // For core candidates (Top / Bottom / OnePiece), select up to MAX_CORE_ROLE_CANDIDATES
        // stratified across itemScores so large wardrobes remain fast while preserving both
        // high-scoring and baseline/unworn candidates.
        // For optional slots (Outerwear / Footwear / Accessory), deduplicate by color + subcategory
        // and cap to MAX_OPTIONAL_ROLE_CANDIDATES to keep completion search fast.
        val byRole: Map<String, List<EngineItem>> = availableItems
            .groupBy { it.outfitRole ?: "Other" }
            .mapValues { (role, roleItems) ->
                val sorted = roleItems.sortedByDescending { itemScores[it.id] ?: 1.0 }
                if (role in OutfitRecommendationEngine.CORE_ROLES) {
                    selectStratifiedControls(sorted, MAX_CORE_ROLE_CANDIDATES)
                } else {
                    sorted.deduplicateByColorAndSubcategory().take(MAX_OPTIONAL_ROLE_CANDIDATES)
                }
            }

        val hasFootwearInPool = availableItems.any { it.outfitRole == OutfitRecommendationEngine.ROLE_FOOTWEAR }
        val optionalSlots = listOf(
            OutfitRecommendationEngine.ROLE_OUTERWEAR,
            OutfitRecommendationEngine.ROLE_FOOTWEAR,
            OutfitRecommendationEngine.ROLE_ACCESSORY,
        ).filter { byRole[it].orEmpty().isNotEmpty() }

        // Enumerate competing cores (excluding the worn core and any other same-day worn core)
        val rawCompetingCores = ArrayList<List<EngineItem>>()
        for (top in byRole[OutfitRecommendationEngine.ROLE_TOP].orEmpty()) {
            for (bottom in byRole[OutfitRecommendationEngine.ROLE_BOTTOM].orEmpty()) {
                val coreIds = setOf(top.id, bottom.id)
                if (coreIds in sameDayWornCores || coreIds == wornCore) continue
                rawCompetingCores.add(listOf(top, bottom))
            }
        }
        for (onePiece in byRole[OutfitRecommendationEngine.ROLE_ONEPIECE].orEmpty()) {
            val coreIds = setOf(onePiece.id)
            if (coreIds in sameDayWornCores || coreIds == wornCore) continue
            rawCompetingCores.add(listOf(onePiece))
        }

        if (rawCompetingCores.isEmpty()) return null

        // Score cores under default weights and pick a balanced set of competing controls:
        // half from the top-scoring cores (hard negatives) and half sampled evenly across the
        // remaining ranked cores (so controls span both worn and unworn/low-habit items).
        val weatherForCoreOnly = if (OutfitRecommendationEngine.ROLE_OUTERWEAR !in optionalSlots) weather else null
        val rankedCores = rawCompetingCores
            .map { coreItems ->
                val s = engine.scoreCombo(
                    items = coreItems,
                    itemScores = itemScores,
                    weather = weatherForCoreOnly,
                    hasFootwearInPool = false,
                    coWear = historyStats.coWear,
                    itemNeighbors = itemNeighbors,
                    subcategoryCoWear = historyStats.subcategoryCoWear,
                    tasteWeights = TasteWeights.DEFAULT,
                )
                coreItems to s
            }
            .sortedWith(
                compareByDescending<Pair<List<EngineItem>, Double>> { it.second }
                    .thenBy { tieBreakKey(it.first, historyStats.lastWornDates) }
            )

        val selectedCores = selectStratifiedControls(rankedCores, maxControls)

        // Extend each competing core to its single best default-scoring completion
        val controlBaselineOffsets = DoubleArray(selectedCores.size)
        val controlFeatures = selectedCores.mapIndexed { idx, (coreItems, _) ->
            val bestCompletion = findBestCompletion(
                coreItems = coreItems,
                optionalSlots = optionalSlots,
                byRole = byRole,
                itemScores = itemScores,
                weather = weather,
                hasFootwearInPool = hasFootwearInPool,
                historyStats = historyStats,
                itemNeighbors = itemNeighbors,
                engine = engine,
            )
            val features = OutfitFeatureExtractor.extract(
                items = bestCompletion,
                itemScores = itemScores,
                weather = weather,
                hasFootwearInPool = hasFootwearInPool,
                coWear = historyStats.coWear,
                itemNeighbors = itemNeighbors,
                subcategoryCoWear = historyStats.subcategoryCoWear,
            )
            val defaultScore = engine.scoreCombo(
                items = bestCompletion,
                itemScores = itemScores,
                weather = weather,
                hasFootwearInPool = hasFootwearInPool,
                coWear = historyStats.coWear,
                itemNeighbors = itemNeighbors,
                subcategoryCoWear = historyStats.subcategoryCoWear,
                tasteWeights = TasteWeights.DEFAULT,
            )
            controlBaselineOffsets[idx] = OutfitFeatureExtractor.baselineOffset(features, defaultScore)
            features
        }

        val chosenFeatures = OutfitFeatureExtractor.extract(
            items = wornItems,
            itemScores = itemScores,
            weather = weather,
            hasFootwearInPool = hasFootwearInPool,
            coWear = historyStats.coWear,
            itemNeighbors = itemNeighbors,
            subcategoryCoWear = historyStats.subcategoryCoWear,
        )
        val chosenDefaultScore = engine.scoreCombo(
            items = wornItems,
            itemScores = itemScores,
            weather = weather,
            hasFootwearInPool = hasFootwearInPool,
            coWear = historyStats.coWear,
            itemNeighbors = itemNeighbors,
            subcategoryCoWear = historyStats.subcategoryCoWear,
            tasteWeights = TasteWeights.DEFAULT,
        )
        val chosenBaselineOffset = OutfitFeatureExtractor.baselineOffset(chosenFeatures, chosenDefaultScore)

        return ChoiceStratum(
            chosenFeatures = chosenFeatures,
            controlFeatures = controlFeatures,
            logId = logId,
            chosenBaselineOffset = chosenBaselineOffset,
            controlBaselineOffsets = controlBaselineOffsets,
        )
    }

    /**
     * Selects up to [maxControls] competing cores from [rankedCores]: takes the top half as hard
     * negatives and spaces the remaining half evenly across the rest of the distribution so the
     * control set includes both high-scoring and baseline/unworn cores.
     */
    private fun <T> selectStratifiedControls(rankedCores: List<T>, maxControls: Int): List<T> {
        if (rankedCores.size <= maxControls) return rankedCores
        val topHalf = maxControls / 2
        val restNeeded = maxControls - topHalf
        val result = ArrayList<T>(maxControls)
        for (i in 0 until topHalf) {
            result.add(rankedCores[i])
        }
        val remaining = rankedCores.size - topHalf
        for (k in 0 until restNeeded) {
            val idx = topHalf + (k * remaining) / restNeeded
            result.add(rankedCores[idx])
        }
        return result
    }

    /**
     * Finds the single highest-default-scoring completion for [coreItems] across [optionalSlots].
     */
    private fun findBestCompletion(
        coreItems: List<EngineItem>,
        optionalSlots: List<String>,
        byRole: Map<String, List<EngineItem>>,
        itemScores: Map<Long, Double>,
        weather: EngineWeather?,
        hasFootwearInPool: Boolean,
        historyStats: PriorHistoryStats,
        itemNeighbors: Map<Long, List<ItemNeighbor>>,
        engine: OutfitRecommendationEngine,
    ): List<EngineItem> {
        if (optionalSlots.isEmpty()) return coreItems
        var partials: List<Pair<List<EngineItem>, Double>> = listOf(coreItems to 1.0)

        optionalSlots.forEachIndexed { index, role ->
            val options: List<EngineItem?> = listOf(null) + byRole[role].orEmpty()
            val decided = optionalSlots.subList(0, index + 1).toSet()
            val slotWeather = if (
                OutfitRecommendationEngine.ROLE_OUTERWEAR in decided ||
                OutfitRecommendationEngine.ROLE_OUTERWEAR !in optionalSlots
            ) weather else null
            val slotHasFootwear = hasFootwearInPool && OutfitRecommendationEngine.ROLE_FOOTWEAR in decided

            val extended = ArrayList<Pair<List<EngineItem>, Double>>(partials.size * options.size)
            for ((partialItems, _) in partials) {
                for (option in options) {
                    val items = if (option == null) partialItems else partialItems + option
                    val s = engine.scoreCombo(
                        items = items,
                        itemScores = itemScores,
                        weather = slotWeather,
                        hasFootwearInPool = slotHasFootwear,
                        coWear = historyStats.coWear,
                        itemNeighbors = itemNeighbors,
                        subcategoryCoWear = historyStats.subcategoryCoWear,
                        tasteWeights = TasteWeights.DEFAULT,
                    )
                    extended.add(items to s)
                }
            }
            partials = extended
                .sortedWith(
                    compareByDescending<Pair<List<EngineItem>, Double>> { it.second }
                        .thenBy { tieBreakKey(it.first, historyStats.lastWornDates) }
                )
                .take(BEAM_PER_CORE_COMPLETION)
        }

        return partials.first().first
    }

    /**
     * Extracts the core item IDs (either 1 Top + 1 Bottom, or 1 OnePiece) from [wornItems].
     * Returns `null` if [wornItems] does not form a valid core.
     */
    private fun extractValidCore(wornItems: List<EngineItem>): Set<Long>? {
        val onePiece = wornItems.firstOrNull { it.outfitRole == OutfitRecommendationEngine.ROLE_ONEPIECE }
        if (onePiece != null) return setOf(onePiece.id)
        val top = wornItems.firstOrNull { it.outfitRole == OutfitRecommendationEngine.ROLE_TOP }
        val bottom = wornItems.firstOrNull { it.outfitRole == OutfitRecommendationEngine.ROLE_BOTTOM }
        if (top != null && bottom != null) return setOf(top.id, bottom.id)
        return null
    }

    // -------------------------------------------------------------------------
    // Causal prior statistics builder (strictly from logs with date < d)
    // -------------------------------------------------------------------------

    private class PriorHistoryStats(
        val coWear: CoWearStats,
        val subcategoryCoWear: CoWearStats,
        val lastWornDates: Map<Long, ItemLastWorn>,
        val tempPercentiles: Map<Long, ItemTempPercentiles>,
        val rainSuitability: Map<Long, ItemRainSuitability>,
        val windSuitability: Map<Long, ItemWindSuitability>,
    )

    private fun buildPriorStats(
        priorLogs: List<HistoricalWearLog>,
        itemById: Map<Long, EngineItem>,
    ): PriorHistoryStats {
        if (priorLogs.isEmpty()) {
            return PriorHistoryStats(
                coWear = CoWearStats.EMPTY,
                subcategoryCoWear = CoWearStats.EMPTY,
                lastWornDates = emptyMap(),
                tempPercentiles = emptyMap(),
                rainSuitability = emptyMap(),
                windSuitability = emptyMap(),
            )
        }

        val pairCounts = HashMap<Pair<Long, Long>, Double>()
        val itemCounts = HashMap<Long, Double>()
        val subPairCounts = HashMap<Pair<Long, Long>, Double>()
        val subItemCounts = HashMap<Long, Double>()
        val lastWorn = HashMap<Long, String>()

        val tempLogsByItem = HashMap<Long, MutableList<Pair<Double, Double>>>()
        val totalLogsByItem = HashMap<Long, Int>()
        val rainHitsByItem = HashMap<Long, Int>()
        val windHitsByItem = HashMap<Long, Int>()

        for (log in priorLogs) {
            val dateStr = log.date.toString()
            val distinctIds = log.itemIds.distinct().sorted()
            for (id in distinctIds) {
                itemCounts[id] = (itemCounts[id] ?: 0.0) + 1.0
                totalLogsByItem[id] = (totalLogsByItem[id] ?: 0) + 1
                lastWorn[id] = dateStr
            }
            for (i in distinctIds.indices) {
                for (j in i + 1 until distinctIds.size) {
                    val key = distinctIds[i] to distinctIds[j]
                    pairCounts[key] = (pairCounts[key] ?: 0.0) + 1.0
                }
            }

            // Subcategory co-wear
            val distinctSubs = distinctIds.mapNotNull { itemById[it]?.subcategoryId }.distinct().sorted()
            for (subId in distinctSubs) {
                subItemCounts[subId] = (subItemCounts[subId] ?: 0.0) + 1.0
            }
            for (i in distinctSubs.indices) {
                for (j in i + 1 until distinctSubs.size) {
                    val subKey = distinctSubs[i] to distinctSubs[j]
                    subPairCounts[subKey] = (subPairCounts[subKey] ?: 0.0) + 1.0
                }
            }

            // Weather suitability observations (rain/wind denominators count all prior snapshots
            // matching RecommendationDao.getRainSuitability / getWindSuitability; temperature counts
            // only logs where both low and high temperatures are present matching getTempLogs).
            val w = log.weather
            if (w != null) {
                for (id in distinctIds) {
                    if (w.isRaining) rainHitsByItem[id] = (rainHitsByItem[id] ?: 0) + 1
                    if (w.isWindy) windHitsByItem[id] = (windHitsByItem[id] ?: 0) + 1
                    if (w.tempLowC != null && w.tempHighC != null) {
                        tempLogsByItem.getOrPut(id) { mutableListOf() }.add(w.tempLowC to w.tempHighC)
                    }
                }
            }
        }

        val total = priorLogs.size.toDouble()
        val tempPercentiles = tempLogsByItem.mapValues { (itemId, obs) ->
            val count = obs.size
            val p10Idx = (count * 0.10).toInt().coerceIn(0, count - 1)
            val p90Idx = (count * 0.90).toInt().coerceIn(0, count - 1)
            val p10Low = obs.sortedBy { it.first }[p10Idx].first
            val p90High = obs.sortedBy { it.second }[p90Idx].second
            ItemTempPercentiles(itemId, p10Low, p90High, count)
        }

        val rainSuitability = totalLogsByItem.mapValues { (itemId, count) ->
            val hits = rainHitsByItem[itemId] ?: 0
            ItemRainSuitability(itemId, hits.toDouble() / count.toDouble(), count)
        }

        val windSuitability = totalLogsByItem.mapValues { (itemId, count) ->
            val hits = windHitsByItem[itemId] ?: 0
            ItemWindSuitability(itemId, hits.toDouble() / count.toDouble(), count)
        }

        return PriorHistoryStats(
            coWear = CoWearStats(pairCounts, itemCounts, total),
            subcategoryCoWear = CoWearStats(subPairCounts, subItemCounts, total),
            lastWornDates = lastWorn.mapValues { (id, d) -> ItemLastWorn(id, d) },
            tempPercentiles = tempPercentiles,
            rainSuitability = rainSuitability,
            windSuitability = windSuitability,
        )
    }

    /**
     * Keeps the first item per [colorDedupKey] (colour names + subcategory), the same key serving
     * uses, so training controls match what the engine would show.
     */
    private fun List<EngineItem>.deduplicateByColorAndSubcategory(): List<EngineItem> {
        val seen = mutableSetOf<String>()
        return filter { item -> seen.add(item.colorDedupKey()) }
    }

    private fun tieBreakKey(items: List<EngineItem>, lastWornDates: Map<Long, ItemLastWorn>): String =
        items.maxOfOrNull { lastWornDates[it.id]?.lastWornDate ?: "" } ?: ""
}
