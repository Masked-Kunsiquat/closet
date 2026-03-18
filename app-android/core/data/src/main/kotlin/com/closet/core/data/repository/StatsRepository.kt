package com.closet.core.data.repository

import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsDao
import com.closet.core.data.dao.StatsOverview
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for closet analytics and wardrobe statistics.
 * Provides high-level insights into wardrobe composition and usage.
 * Parity: This is the native equivalent of Stats section in hangr/db/queries.ts.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao
) {
    /**
     * Retrieves headline statistics (total items, worn/never-worn counts, and total value) for active items.
     * @param fromDate Optional start date (YYYY-MM-DD) to filter usage statistics.
     * @return A [Flow] emitting the [StatsOverview].
     */
    fun getStatsOverview(fromDate: String? = null): Flow<StatsOverview> = 
        statsDao.getStatsOverview(fromDate)

    /**
     * Retrieves a list of the most frequently worn active items.
     * @param fromDate Optional start date to filter usage logs.
     * @param limit The maximum number of items to retrieve (default is 15).
     * @return A [Flow] emitting a list of [StatItem] objects.
     */
    fun getMostWornItems(fromDate: String? = null, limit: Int = 15): Flow<List<StatItem>> = 
        statsDao.getMostWornItems(fromDate, limit)

    /**
     * Retrieves a breakdown of active items by their categories.
     * @return A [Flow] emitting a list of [BreakdownRow] showing item counts per category.
     */
    fun getCategoryBreakdown(): Flow<List<BreakdownRow>> = 
        statsDao.getBreakdownByCategory()
}
