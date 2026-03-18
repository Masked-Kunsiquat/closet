package com.closet.core.data.repository

import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsDao
import com.closet.core.data.dao.StatsOverview
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for closet analytics.
 * Parity: Mirror of Stats section in hangr/db/queries.ts.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao
) {
    /**
     * Headline statistics (total items, worn/never-worn counts, total value).
     * @param fromDate Optional YYYY-MM-DD date filter.
     */
    fun getStatsOverview(fromDate: String? = null): Flow<StatsOverview> = 
        statsDao.getStatsOverview(fromDate)

    /**
     * Top worn items.
     */
    fun getMostWornItems(fromDate: String? = null, limit: Int = 15): Flow<List<StatItem>> = 
        statsDao.getMostWornItems(fromDate, limit)

    /**
     * Item count per category.
     */
    fun getCategoryBreakdown(): Flow<List<BreakdownRow>> = 
        statsDao.getBreakdownByCategory()
}
