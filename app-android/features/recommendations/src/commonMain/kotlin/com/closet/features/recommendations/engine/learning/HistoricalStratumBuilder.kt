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
import kotlinx.datetime.LocalDate
import kotlin.random.Random

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
 *   Up to [MAX_CONTROLS_PER_STRATUM] of them are drawn **uniformly** (seeded by the log id), and
 *   each is completed with the worn outfit's number of outerwear, footwear and accessory items,
 *   drawn uniformly from each role's pool. See [sampleControlOutfits].
 *
 * ### Why uniform
 * A conditional logit fitted on a uniform sample of alternatives is consistent without a
 * correction term; controls ranked or completed by the default rules invert the learned weights
 * (#135). Matching the optional slots holds the footwear feature, and the layering feature when
 * only outerwear carries the `Outer` warmth layer, constant within a stratum, so they are never
 * learned.
 *
 * Layering stays frozen only because the seeded categories map the `Outer` warmth layer one to
 * one to the Outerwear role (`DatabaseSeeder.seedCategories` in `core/data`: only "Outerwear" is
 * `Outer`, and it is the only `Outerwear`-role category). A category that broke that mapping would
 * let layering vary between chosen and controls within a stratum, and it would be learned again.
 *
 * The chosen outfit is scored on its projection to the controls' structure (core plus optional
 * roles; see [projectToControlStructure]), so both sides of a stratum have the same shape.
 */
internal object HistoricalStratumBuilder {

    /** Maximum number of competing control cores kept per stratum (matches engine [OutfitRecommendationEngine.CORE_BEAM]). */
    internal const val MAX_CONTROLS_PER_STRATUM = OutfitRecommendationEngine.CORE_BEAM

    /** Maximum number of recent choice strata constructed for training (older logs still feed causal history). */
    internal const val MAX_TRAINING_STRATA = 120

    private val OPTIONAL_ROLE_ORDER = listOf(
        OutfitRecommendationEngine.ROLE_OUTERWEAR,
        OutfitRecommendationEngine.ROLE_FOOTWEAR,
        OutfitRecommendationEngine.ROLE_ACCESSORY,
    )

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
     * @param engine         Engine instance used for per-item suitability scoring and each candidate's default score (baseline offset).
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
                Triple(log, projectToControlStructure(wornItems, core), core)
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

        val controlOutfits = sampleControlOutfits(
            logId = logId,
            wornItems = wornItems,
            wornCore = wornCore,
            sameDayWornCores = sameDayWornCores,
            availableItems = availableItems,
            maxControls = maxControls,
        )
        if (controlOutfits.isEmpty()) return null

        val itemScores: Map<Long, Double> = availableItems.associate { item ->
            item.id to engine.itemScore(item, input)
        }
        val hasFootwearInPool = availableItems.any { it.outfitRole == OutfitRecommendationEngine.ROLE_FOOTWEAR }

        val controlBaselineOffsets = DoubleArray(controlOutfits.size)
        val controlFeatures = controlOutfits.mapIndexed { idx, controlItems ->
            val features = OutfitFeatureExtractor.extract(
                items = controlItems,
                itemScores = itemScores,
                weather = weather,
                hasFootwearInPool = hasFootwearInPool,
                coWear = historyStats.coWear,
                itemNeighbors = itemNeighbors,
                subcategoryCoWear = historyStats.subcategoryCoWear,
            )
            val defaultScore = engine.scoreCombo(
                items = controlItems,
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
            choiceKey = wornCore,
        )
    }

    /**
     * Projects a worn outfit onto the structure every control has: its [core] plus the items in
     * an optional role (Outerwear, Footwear, Accessory). A second top (a sweater over a shirt),
     * a second bottom, and `Other`-role items (bags, socks, underwear) never appear in a control,
     * and serving never suggests them either. Scoring them on the chosen side only would make
     * chosen and controls differ in structure, the same class of bias as #135, so the chosen
     * features, its baseline offset and the matched slot counts all come from this projection.
     */
    private fun projectToControlStructure(wornItems: List<EngineItem>, core: Set<Long>): List<EngineItem> =
        wornItems.filter { it.id in core || it.outfitRole in OPTIONAL_ROLE_ORDER }

    /**
     * Draws the control outfits for one stratum: uniform, seeded samples that are never ranked by
     * the rules being learned (McFadden's uniform-conditioning property; design D1 and D2, #135).
     *
     * In this order, from one `Random(logId)`:
     * 1. The competing cores are every Top × Bottom pair and every OnePiece (`T·B + O` of them,
     *    each role sorted by id), excluding [wornCore] and [sameDayWornCores].
     * 2. At most [maxControls] of them are drawn uniformly without replacement, by index over that
     *    space, so every competing core is equally likely whatever the role sizes. The pairs are
     *    never materialised: the draw costs O([maxControls]), not O(T·B).
     * 3. Each sampled core, in sampled order, gets the worn outfit's item count for each optional
     *    role (Outerwear, Footwear, Accessory, in that order), drawn uniformly without replacement
     *    from that role's full available pool. So whether to wear shoes or a layer is matched,
     *    never learned.
     *
     * Every pool is sorted by id before it is shuffled, so the input order never reaches the draws.
     * A control may reuse a worn item; only the worn core itself is excluded.
     *
     * @return The control outfits' items (core first, then the optional slots); empty when no
     *         competing core exists.
     */
    internal fun sampleControlOutfits(
        logId: Long,
        wornItems: List<EngineItem>,
        wornCore: Set<Long>,
        sameDayWornCores: Set<Set<Long>>,
        availableItems: List<EngineItem>,
        maxControls: Int,
    ): List<List<EngineItem>> {
        val rnd = Random(logId)
        val poolByRole: Map<String?, List<EngineItem>> = availableItems
            .sortedBy { it.id }
            .groupBy { it.outfitRole }

        val tops = poolByRole[OutfitRecommendationEngine.ROLE_TOP].orEmpty()
        val bottoms = poolByRole[OutfitRecommendationEngine.ROLE_BOTTOM].orEmpty()
        val onePieces = poolByRole[OutfitRecommendationEngine.ROLE_ONEPIECE].orEmpty()
        val pairCount = tops.size.toLong() * bottoms.size
        val coreSpace = pairCount + onePieces.size

        fun coreAt(index: Long): List<EngineItem> =
            if (index < pairCount) {
                listOf(tops[(index / bottoms.size).toInt()], bottoms[(index % bottoms.size).toInt()])
            } else {
                listOf(onePieces[(index - pairCount).toInt()])
            }

        // The index of each excluded core that lies in the space (a core whose items are not all
        // available, or not in a core role, is not in it).
        val topIndex = tops.withIndex().associate { (i, item) -> item.id to i }
        val bottomIndex = bottoms.withIndex().associate { (i, item) -> item.id to i }
        val onePieceIndex = onePieces.withIndex().associate { (i, item) -> item.id to i }
        fun indexOf(core: Set<Long>): Long? = when (core.size) {
            1 -> onePieceIndex[core.first()]?.let { pairCount + it }
            2 -> {
                val (a, b) = core.toList()
                val t = topIndex[a] ?: topIndex[b]
                val bt = bottomIndex[a] ?: bottomIndex[b]
                if (t != null && bt != null) t.toLong() * bottoms.size + bt else null
            }
            else -> null
        }
        val excluded: Set<Long> = (sameDayWornCores + setOf(wornCore)).mapNotNullTo(HashSet()) { indexOf(it) }

        val validCount = coreSpace - excluded.size
        if (validCount <= 0L) return emptyList()
        val sampledIndices: List<Long> = if (validCount <= maxControls) {
            (0L until coreSpace).filter { it !in excluded }.shuffled(rnd)
        } else {
            // Rejection sampling: at most a few excluded indices, so this stays O(maxControls).
            val seen = LinkedHashSet<Long>()
            while (seen.size < maxControls) {
                val index = rnd.nextLong(coreSpace)
                if (index !in excluded) seen.add(index)
            }
            seen.toList()
        }
        val sampledCores = sampledIndices.map(::coreAt)

        val wornCounts = OPTIONAL_ROLE_ORDER.associateWith { role -> wornItems.count { it.outfitRole == role } }
        return sampledCores.map { core ->
            val outfit = ArrayList<EngineItem>(core)
            for (role in OPTIONAL_ROLE_ORDER) {
                val count = wornCounts.getValue(role)
                if (count == 0) continue
                outfit.addAll(poolByRole[role].orEmpty().shuffled(rnd).take(count))
            }
            outfit
        }
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
}
