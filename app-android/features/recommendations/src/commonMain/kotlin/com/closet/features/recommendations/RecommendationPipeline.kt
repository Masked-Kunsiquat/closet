package com.closet.features.recommendations

import com.closet.core.data.dao.ItemColorFamily
import com.closet.core.data.dao.ItemColorHex
import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemPatternName
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.ColorHex
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.buildItemNeighbors
import com.closet.features.recommendations.engine.formalityLevel
import com.closet.features.recommendations.engine.learning.TasteWeights
import com.closet.features.recommendations.model.WeatherConditions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.random.Random

/** One pipeline run: what to recommend for, how many, what to skip, and the selection jitter. */
internal data class PipelineRequest(
    val today: LocalDate,
    val occasionId: Long?,
    val weather: WeatherConditions?,
    val topN: Int,
    val exclude: Set<Set<Long>> = emptySet(),
    val jitter: Random,
)

/**
 * The data-gathering half of the recommendation flow, without the ViewModel: fetches the
 * hard-filtered candidates, runs the score queries in parallel, builds the [EngineInput] and
 * runs [OutfitRecommendationEngine.recommend]. Session state (exclusion wrap-around), AI
 * readiness and the AI scorer stay with the caller.
 *
 * @param tasteWeights resolves the [TasteWeights] for the given total wear-log count.
 */
internal class RecommendationPipeline(
    private val recommendationRepository: RecommendationRepository,
    private val engine: OutfitRecommendationEngine,
    private val computationDispatcher: CoroutineDispatcher,
    private val tasteWeights: suspend (totalLogCount: Int) -> TasteWeights,
) {
    /** Candidates → parallel queries → EngineInput → one engine.recommend. Empty candidates → emptyList(). */
    suspend fun run(request: PipelineRequest): List<OutfitCombo> {
        val input = engineInput(request) ?: return emptyList()
        return recommend(input, request)
    }

    /** Runs the engine once on an already-built [input], with [request]'s topN, exclusion and jitter. */
    fun recommend(input: EngineInput, request: PipelineRequest): List<OutfitCombo> =
        engine.recommend(input, topN = request.topN, exclude = request.exclude, jitter = request.jitter)

    /**
     * Builds the EngineInput without running the engine; null when there are no candidates.
     *
     * All score queries run concurrently via [async]/[coroutineScope]. A [DataResult.Error] from
     * any query is converted to an [IllegalStateException] so the caller can handle it uniformly.
     */
    suspend fun engineInput(request: PipelineRequest): EngineInput? = coroutineScope {
        val today = request.today
        val weather = request.weather
        val calendarSeason = seasonFor(today.month)

        // 1. Fetch candidates (sequential — item IDs are needed for subsequent queries)
        val candidatesResult = recommendationRepository.getCandidates(
            calendarSeason = calendarSeason,
            tempLow = weather?.tempLowC,
            tempHigh = weather?.tempHighC,
            occasionId = request.occasionId
        )
        val candidates = candidatesResult.unwrapOrThrow("getCandidates")
        if (candidates.isEmpty()) return@coroutineScope null

        val itemIds = candidates.map { it.id }

        // 2. Run all score queries in parallel
        val tempPercentilesDeferred = async {
            recommendationRepository.getTempPercentiles(itemIds).unwrapOrThrow("getTempPercentiles")
        }
        val rainDeferred = async {
            recommendationRepository.getRainSuitability(itemIds).unwrapOrThrow("getRainSuitability")
        }
        val windDeferred = async {
            recommendationRepository.getWindSuitability(itemIds).unwrapOrThrow("getWindSuitability")
        }
        val lastWornDeferred = async {
            recommendationRepository.getLastWornDates(itemIds).unwrapOrThrow("getLastWornDates")
        }
        val colorFamiliesDeferred = async {
            recommendationRepository.getItemColorFamilies(itemIds).unwrapOrThrow("getItemColorFamilies")
        }
        val patternNamesDeferred = async {
            recommendationRepository.getItemPatternNames(itemIds).unwrapOrThrow("getItemPatternNames")
        }
        val logPairsDeferred = async {
            recommendationRepository.getCoWearPairs(itemIds).unwrapOrThrow("getCoWearPairs")
        }
        val logItemCountsDeferred = async {
            recommendationRepository.getItemLogCounts(itemIds).unwrapOrThrow("getItemLogCounts")
        }
        val logTotalDeferred = async {
            recommendationRepository.getTotalLogCount().unwrapOrThrow("getTotalLogCount")
        }
        val savedPairsDeferred = async {
            recommendationRepository.getSavedOutfitPairs(itemIds).unwrapOrThrow("getSavedOutfitPairs")
        }
        val savedItemCountsDeferred = async {
            recommendationRepository.getSavedItemCounts(itemIds).unwrapOrThrow("getSavedItemCounts")
        }
        val savedTotalDeferred = async {
            recommendationRepository.getSavedOutfitCount().unwrapOrThrow("getSavedOutfitCount")
        }
        val occasionNamesDeferred = async {
            recommendationRepository.getItemOccasionNames(itemIds).unwrapOrThrow("getItemOccasionNames")
        }
        val colorHexesDeferred = async {
            recommendationRepository.getItemColorHexes(itemIds).unwrapOrThrow("getItemColorHexes")
        }
        val embeddingsDeferred = async {
            recommendationRepository.getItemEmbeddings(itemIds).unwrapOrThrow("getItemEmbeddings")
        }
        val subcategoryLogPairsDeferred = async {
            recommendationRepository.getSubcategoryCoWearPairs().unwrapOrThrow("getSubcategoryCoWearPairs")
        }
        val subcategorySavedPairsDeferred = async {
            recommendationRepository.getSubcategorySavedOutfitPairs().unwrapOrThrow("getSubcategorySavedOutfitPairs")
        }
        val subcategoryLogCountsDeferred = async {
            recommendationRepository.getSubcategoryLogCounts().unwrapOrThrow("getSubcategoryLogCounts")
        }
        val subcategorySavedCountsDeferred = async {
            recommendationRepository.getSubcategorySavedItemCounts().unwrapOrThrow("getSubcategorySavedItemCounts")
        }
        val subcategoryNamesDeferred = async {
            recommendationRepository.getItemSubcategoryNames(itemIds).unwrapOrThrow("getItemSubcategoryNames")
        }
        val materialNamesDeferred = async {
            recommendationRepository.getItemMaterialNames(itemIds).unwrapOrThrow("getItemMaterialNames")
        }

        val tempPercentiles: List<ItemTempPercentiles> = tempPercentilesDeferred.await()
        val rain: List<ItemRainSuitability> = rainDeferred.await()
        val wind: List<ItemWindSuitability> = windDeferred.await()
        val lastWorn: List<ItemLastWorn> = lastWornDeferred.await()
        val colorFamilyRows: List<ItemColorFamily> = colorFamiliesDeferred.await()
        val patternNameRows: List<ItemPatternName> = patternNamesDeferred.await()
        val occasionNamesMap: Map<Long, List<String>> = occasionNamesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.occasionName })
        val colorHexesMap: Map<Long, List<ColorHex>> = colorHexesDeferred.await()
            .groupBy({ it.clothingItemId }, { it.toColorHex() })
        val subcategoryNamesMap: Map<Long, String> = subcategoryNamesDeferred.await()
        val materialNamesMap: Map<Long, Set<String>> = materialNamesDeferred.await()

        val saved = CoWearStats.SAVED_OUTFIT_WEIGHT
        val pairCounts = HashMap<Pair<Long, Long>, Double>()
        logPairsDeferred.await().forEach { pairCounts.addTo(it.aId to it.bId, it.count.toDouble()) }
        savedPairsDeferred.await().forEach { pairCounts.addTo(it.aId to it.bId, it.count * saved) }
        val itemCounts = HashMap<Long, Double>()
        logItemCountsDeferred.await().forEach { itemCounts.addTo(it.clothingItemId, it.count.toDouble()) }
        savedItemCountsDeferred.await().forEach { itemCounts.addTo(it.clothingItemId, it.count * saved) }
        val totalLogCount = logTotalDeferred.await()
        val totalHistory = totalLogCount + saved * savedTotalDeferred.await()
        val coWear = CoWearStats(
            pairCounts = pairCounts,
            itemCounts = itemCounts,
            total = totalHistory,
        )

        val embeddingsMap = embeddingsDeferred.await()
        val itemNeighbors = withContext(computationDispatcher) {
            buildItemNeighbors(embeddingsMap)
        }

        val subcategoryPairCounts = HashMap<Pair<Long, Long>, Double>()
        subcategoryLogPairsDeferred.await().forEach { subcategoryPairCounts.addTo(it.aId to it.bId, it.count.toDouble()) }
        subcategorySavedPairsDeferred.await().forEach { subcategoryPairCounts.addTo(it.aId to it.bId, it.count * saved) }
        val subcategoryItemCounts = HashMap<Long, Double>()
        subcategoryLogCountsDeferred.await().forEach { subcategoryItemCounts.addTo(it.clothingItemId, it.count.toDouble()) }
        subcategorySavedCountsDeferred.await().forEach { subcategoryItemCounts.addTo(it.clothingItemId, it.count * saved) }
        val subcategoryCoWear = CoWearStats(
            pairCounts = subcategoryPairCounts,
            itemCounts = subcategoryItemCounts,
            total = totalHistory,
        )

        // 3. Index score results by item ID for O(1) lookup
        val tempMap: Map<Long, ItemTempPercentiles> =
            tempPercentiles.associateBy { it.clothingItemId }
        val rainMap: Map<Long, ItemRainSuitability> =
            rain.associateBy { it.clothingItemId }
        val windMap: Map<Long, ItemWindSuitability> =
            wind.associateBy { it.clothingItemId }
        val lastWornMap: Map<Long, ItemLastWorn> =
            lastWorn.associateBy { it.clothingItemId }

        // Group color families into per-item sets
        val colorFamilyMap: Map<Long, Set<String>> =
            colorFamilyRows
                .groupBy { it.clothingItemId }
                .mapValues { (_, rows) -> rows.map { it.colorFamily }.toSet() }

        // Group pattern names into per-item lists; derive solid flag
        val patternNamesMap: Map<Long, List<String>> =
            patternNameRows
                .groupBy { it.clothingItemId }
                .mapValues { (_, rows) -> rows.map { it.patternName } }

        // 4. Map CandidateItem → EngineItem
        val engineItems: List<EngineItem> = candidates.map { item ->
            val patternNames = patternNamesMap[item.id] ?: emptyList()
            EngineItem(
                id = item.id,
                name = item.name,
                imagePath = item.imagePath,
                categoryId = item.categoryId,
                subcategoryId = item.subcategoryId,
                outfitRole = item.outfitRole,
                warmthLayer = item.warmthLayer,
                colorFamilies = colorFamilyMap[item.id] ?: emptySet(),
                isPatternSolid = patternNames.isEmpty() || patternNames.all { it == "Solid" },
                colorHexes = colorHexesMap[item.id] ?: emptyList(),
                formalityLevel = formalityLevel(occasionNamesMap[item.id] ?: emptyList()),
                subcategoryName = subcategoryNamesMap[item.id],
                materials = materialNamesMap[item.id] ?: emptySet(),
            )
        }

        // 5. Resolve taste weights (the caller decides: DEFAULT while learned taste is paused).
        val tasteWeights = tasteWeights(totalLogCount)

        // 6. Build EngineInput
        val engineWeather = weather?.toEngineWeather()
        EngineInput(
            candidates = engineItems,
            tempPercentiles = tempMap,
            rainSuitability = rainMap,
            windSuitability = windMap,
            lastWornDates = lastWornMap,
            weather = engineWeather,
            today = today,
            coWear = coWear,
            itemNeighbors = itemNeighbors,
            subcategoryCoWear = subcategoryCoWear,
            tasteWeights = tasteWeights,
        )
    }
}

/**
 * Maps a feature-layer [WeatherConditions] to the engine's [EngineWeather].
 *
 * Both types carry identical fields — the mapping exists to keep the engine
 * package free of any dependency on the feature model layer.
 */
private fun WeatherConditions.toEngineWeather(): EngineWeather = EngineWeather(
    tempLowC = tempLowC,
    tempHighC = tempHighC,
    isRaining = isRaining,
    isWindy = isWindy
)

private fun <K> MutableMap<K, Double>.addTo(key: K, amount: Double) {
    this[key] = (this[key] ?: 0.0) + amount
}

/**
 * Unwraps a [DataResult.Success] payload or throws an [IllegalStateException] with
 * a message that includes [operationName]. [DataResult.Loading] is treated as an
 * impossible state for suspend functions and also throws.
 */
internal fun <T> DataResult<T>.unwrapOrThrow(operationName: String): T = when (this) {
    is DataResult.Success -> data
    is DataResult.Error -> throw IllegalStateException(
        "RecommendationViewModel: $operationName failed", throwable
    )
    DataResult.Loading -> throw IllegalStateException(
        "RecommendationViewModel: $operationName returned Loading — this should never happen for suspend calls"
    )
}

/**
 * The engine's view of one colour tag: the measured hex when there is one, else the name hex,
 * which is also kept as the fallback for a measured hex that does not parse (color v2, #81).
 */
internal fun ItemColorHex.toColorHex() = ColorHex(
    hex = measuredHex ?: hex,
    family = colorFamily,
    share = share,
    wearsAsNeutral = wearsAsNeutral,
    fallbackHex = hex,
)
