package com.closet.core.data.repository

import com.closet.core.data.dao.CandidateItem
import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import com.closet.core.data.dao.RecommendationDao
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class RecommendationRepository @Inject constructor(
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
                Timber.e(e, "RecommendationRepository.$operationName failed")
                DataResult.Error(AppError.DatabaseError.QueryError(e))
            }
        )
}
