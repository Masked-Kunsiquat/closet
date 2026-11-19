package com.closet.core.data.repository

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.dao.ItemColorFamily
import com.closet.core.data.dao.ItemColorHex
import com.closet.core.data.dao.ItemCount
import com.closet.core.data.dao.ItemOccasionName
import com.closet.core.data.dao.ItemPair
import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemPatternName
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import com.closet.core.data.dao.RecommendationDao
import com.closet.core.data.dao.TrainingItemRow
import com.closet.core.data.dao.TrainingWearLogItemRow
import com.closet.core.data.dao.TrainingWearLogRow
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.toFloatArrayLE
import com.closet.core.data.worker.EmbeddingWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import co.touchlab.kermit.Logger

/**
 * Combined AI context hint for a single clothing item.
 *
 * Holds the human-readable strings needed by the AI stylist prompt builder that are
 * not available on [com.closet.features.recommendations.engine.EngineItem]. Merged
 * from two separate DAO queries by [RecommendationRepository.getAiContextHints].
 *
 * @property clothingItemId  FK to clothing_items.
 * @property subcategoryName Subcategory label (e.g. "Button-down"), or null if none assigned.
 * @property primaryMaterial First material name (e.g. "Cotton"), or null if none tagged.
 */
data class ItemAiContextHint(
    val clothingItemId: Long,
    val subcategoryName: String?,
    val primaryMaterial: String?,
)

/**
 * Repository for the outfit recommendation data layer.
 *
 * All methods return [DataResult] and re-throw [CancellationException] so that
 * coroutine cancellation propagates correctly. Raw exceptions are wrapped as
 * [AppError.DatabaseError.QueryError].
 *
 * This repository does not call WeatherRepository — callers (ViewModels) are
 * responsible for resolving weather data and passing it in as plain parameters.
 */
class RecommendationRepository(
    private val recommendationDao: RecommendationDao
) {

    /**
     * Returns all Active + Clean clothing items that pass the season and optional
     * occasion hard filters.
     *
     * @param calendarSeason Current calendar season name (e.g. "Winter").
     * @param tempLow        Today's forecast low °C, or null if weather was skipped.
     * @param tempHigh       Today's forecast high °C, or null if weather was skipped.
     * @param occasionId     Required occasion ID, or null to skip the occasion filter.
     */
    suspend fun getCandidates(
        calendarSeason: String,
        tempLow: Double?,
        tempHigh: Double?,
        occasionId: Long?
    ): DataResult<List<CandidateItem>> = runCatching {
        recommendationDao.getCandidates(calendarSeason, tempLow, tempHigh, occasionId)
    }.toDataResult("getCandidates")

    /**
     * Returns temperature percentile bounds for each item in [itemIds].
     *
     * Fetches raw temperature log rows from the DAO and derives the 10th/90th
     * percentile positions in Kotlin. SQLite's correlated-subquery OFFSET pattern
     * for percentiles is not supported by Room's compile-time SQL validator, so
     * the computation happens here instead.
     *
     * Items with no temperature logs are omitted from the result map. The engine
     * treats missing entries as insufficient data and skips the temperature signal
     * for those items.
     *
     * Percentile algorithm: for N sorted values, the value at index
     * `floor(N * fraction)` (clamped to [0, N-1]) is used as the percentile.
     * This matches the `ORDER BY … LIMIT 1 OFFSET floor(N * 0.1)` pattern from
     * the spec.
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getTempPercentiles(
        itemIds: List<Long>
    ): DataResult<List<ItemTempPercentiles>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            val rows = recommendationDao.getTempLogs(itemIds)
            // Rows are ordered by (clothing_item_id ASC, temperature_low ASC)
            rows.groupBy { it.clothingItemId }.map { (itemId, logs) ->
                val count = logs.size
                val p10Index = (count * 0.10).toInt().coerceIn(0, count - 1)
                val p90Index = (count * 0.90).toInt().coerceIn(0, count - 1)
                // p10TempLow: use rows sorted by temperature_low (already the DAO order)
                val p10TempLow = logs[p10Index].temperatureLow
                // p90TempHigh: must sort by temperature_high independently — the low-sort
                // order does not guarantee high values are in the same rank position.
                val p90TempHigh = logs.sortedBy { it.temperatureHigh }[p90Index].temperatureHigh
                ItemTempPercentiles(
                    clothingItemId = itemId,
                    p10TempLow = p10TempLow,
                    p90TempHigh = p90TempHigh,
                    logCount = count
                )
            }
        }.toDataResult("getTempPercentiles")
    }

    /**
     * Returns rain suitability scores for each item in [itemIds].
     * Items with no logs are omitted; the engine skips the rain signal for them.
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getRainSuitability(
        itemIds: List<Long>
    ): DataResult<List<ItemRainSuitability>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getRainSuitability(itemIds)
        }.toDataResult("getRainSuitability")
    }

    /**
     * Returns wind suitability scores for each item in [itemIds].
     * Items with no logs are omitted; the engine skips the wind signal for them.
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getWindSuitability(
        itemIds: List<Long>
    ): DataResult<List<ItemWindSuitability>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getWindSuitability(itemIds)
        }.toDataResult("getWindSuitability")
    }

    /**
     * Returns the last-worn date for each item in [itemIds].
     * Used for tie-breaking only (prefer items worn least recently).
     * Items that have never been logged are omitted; the engine treats them as
     * "never worn" and ranks them highest in ties.
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getLastWornDates(
        itemIds: List<Long>
    ): DataResult<List<ItemLastWorn>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getLastWornDates(itemIds)
        }.toDataResult("getLastWornDates")
    }

    /**
     * Returns color family rows for each item in [itemIds].
     *
     * One row per (item, color_family) pair — items with no colors are omitted.
     * The ViewModel groups these into a per-item [Set] of color family strings and
     * treats a missing item as having an empty set (the engine treats that as Neutral).
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getItemColorFamilies(
        itemIds: List<Long>
    ): DataResult<List<ItemColorFamily>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getItemColorFamilies(itemIds)
        }.toDataResult("getItemColorFamilies")
    }

    /**
     * Returns pattern name rows for each item in [itemIds].
     *
     * One row per (item, pattern_name) pair — items with no patterns are omitted.
     * The ViewModel groups these into a per-item list of pattern names and derives
     * [com.closet.features.recommendations.engine.EngineItem.isPatternSolid]:
     * solid when the list is empty or every name equals "Solid".
     *
     * @param itemIds Candidate item IDs from [getCandidates].
     */
    suspend fun getItemPatternNames(
        itemIds: List<Long>
    ): DataResult<List<ItemPatternName>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getItemPatternNames(itemIds)
        }.toDataResult("getItemPatternNames")
    }

    /**
     * Returns AI prompt enrichment context for each item in [itemIds].
     *
     * Runs [RecommendationDao.getItemSubcategoryHints] and
     * [RecommendationDao.getItemMaterialHints] in parallel, then merges the results
     * into a map keyed by clothing item ID. Items absent from either query (no
     * subcategory assigned / no materials tagged) are represented with null fields
     * rather than being omitted from the map.
     *
     * @param itemIds Clothing item IDs to enrich (typically the engine's output set).
     * @return Map from clothing_item_id to [ItemAiContextHint] for every ID in [itemIds].
     */
    suspend fun getAiContextHints(
        itemIds: List<Long>
    ): DataResult<Map<Long, ItemAiContextHint>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyMap())
        return try {
            coroutineScope {
                val subcategoryDeferred = async { recommendationDao.getItemSubcategoryHints(itemIds) }
                val materialDeferred = async { recommendationDao.getItemMaterialHints(itemIds) }

                val subcategoryRows = subcategoryDeferred.await()
                val materialRows = materialDeferred.await()

                val subcategoryByItem = subcategoryRows.associateBy { it.clothingItemId }
                val materialByItem = materialRows.groupBy { it.clothingItemId }

                val result = itemIds.associateWith { id ->
                    ItemAiContextHint(
                        clothingItemId = id,
                        subcategoryName = subcategoryByItem[id]?.subcategoryName,
                        primaryMaterial = materialByItem[id]?.firstOrNull()?.materialName,
                    )
                }
                DataResult.Success(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.withTag("RecommendationRepository").e(e) { "RecommendationRepository.getAiContextHints failed" }
            DataResult.Error(AppError.DatabaseError.QueryError(e))
        }
    }

    // Compatibility signals (engine v2): co-wear, saved outfits, occasions, hex.

    suspend fun getCoWearPairs(
        itemIds: List<Long>
    ): DataResult<List<ItemPair>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getCoWearPairs(itemIds)
        }.toDataResult("getCoWearPairs")
    }

    suspend fun getItemLogCounts(
        itemIds: List<Long>
    ): DataResult<List<ItemCount>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getItemLogCounts(itemIds)
        }.toDataResult("getItemLogCounts")
    }

    suspend fun getTotalLogCount(): DataResult<Int> =
        runCatching { recommendationDao.getTotalLogCount() }.toDataResult("getTotalLogCount")

    suspend fun getSavedOutfitPairs(
        itemIds: List<Long>
    ): DataResult<List<ItemPair>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getSavedOutfitPairs(itemIds)
        }.toDataResult("getSavedOutfitPairs")
    }

    suspend fun getSavedItemCounts(
        itemIds: List<Long>
    ): DataResult<List<ItemCount>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getSavedItemCounts(itemIds)
        }.toDataResult("getSavedItemCounts")
    }

    suspend fun getSavedOutfitCount(): DataResult<Int> =
        runCatching { recommendationDao.getSavedOutfitCount() }.toDataResult("getSavedOutfitCount")

    suspend fun getItemOccasionNames(
        itemIds: List<Long>
    ): DataResult<List<ItemOccasionName>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getItemOccasionNames(itemIds)
        }.toDataResult("getItemOccasionNames")
    }

    suspend fun getItemColorHexes(
        itemIds: List<Long>
    ): DataResult<List<ItemColorHex>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyList())
        return runCatching {
            recommendationDao.getItemColorHexes(itemIds)
        }.toDataResult("getItemColorHexes")
    }

    // History generalization (engine v2, Delivery 3, #96): embeddings and subcategory co-wears.

    suspend fun getItemEmbeddings(
        itemIds: List<Long>,
        modelVersion: String = EmbeddingWork.MODEL_VERSION,
    ): DataResult<Map<Long, FloatArray>> {
        if (itemIds.isEmpty()) return DataResult.Success(emptyMap())
        return runCatching {
            recommendationDao.getItemEmbeddings(itemIds, modelVersion)
                .associate { it.itemId to it.embeddingBlob.toFloatArrayLE() }
        }.toDataResult("getItemEmbeddings")
    }

    suspend fun getSubcategoryCoWearPairs(): DataResult<List<ItemPair>> =
        runCatching { recommendationDao.getSubcategoryCoWearPairs() }.toDataResult("getSubcategoryCoWearPairs")

    suspend fun getSubcategorySavedOutfitPairs(): DataResult<List<ItemPair>> =
        runCatching { recommendationDao.getSubcategorySavedOutfitPairs() }.toDataResult("getSubcategorySavedOutfitPairs")

    suspend fun getSubcategoryLogCounts(): DataResult<List<ItemCount>> =
        runCatching { recommendationDao.getSubcategoryLogCounts() }.toDataResult("getSubcategoryLogCounts")

    suspend fun getSubcategorySavedItemCounts(): DataResult<List<ItemCount>> =
        runCatching { recommendationDao.getSubcategorySavedItemCounts() }.toDataResult("getSubcategorySavedItemCounts")

    // Learned taste weights training (engine v2, #114): watermark and historical log snapshots.

    suspend fun getMaxLogId(): DataResult<Long?> =
        runCatching { recommendationDao.getMaxLogId() }.toDataResult("getMaxLogId")

    suspend fun getTrainingItems(): DataResult<List<TrainingItemRow>> =
        runCatching { recommendationDao.getTrainingItems() }.toDataResult("getTrainingItems")

    suspend fun getTrainingWearLogs(): DataResult<List<TrainingWearLogRow>> =
        runCatching { recommendationDao.getTrainingWearLogs() }.toDataResult("getTrainingWearLogs")

    suspend fun getTrainingWearLogItems(): DataResult<List<TrainingWearLogItemRow>> =
        runCatching { recommendationDao.getTrainingWearLogItems() }.toDataResult("getTrainingWearLogItems")

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Converts a [Result] to a [DataResult], re-throwing [CancellationException]
     * and wrapping all other failures as [AppError.DatabaseError.QueryError].
     */
    private fun <T> Result<T>.toDataResult(operationName: String): DataResult<T> =
        fold(
            onSuccess = { DataResult.Success(it) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                Logger.withTag("RecommendationRepository").e(e) { "RecommendationRepository.$operationName failed" }
                DataResult.Error(AppError.DatabaseError.QueryError(e))
            }
        )
}
