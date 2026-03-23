package com.closet.core.data.repository

import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CategorySubcategoryRow
import com.closet.core.data.dao.ColorBreakdownRow
import com.closet.core.data.dao.CostPerWearItem
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
     * Retrieves item counts grouped by category and subcategory for the combined breakdown view.
     * Items without a subcategory fall back to the category name as their subcategory label.
     * @return A [Flow] emitting a list of [CategorySubcategoryRow].
     */
    fun getCategorySubcategoryBreakdown(): Flow<List<CategorySubcategoryRow>> =
        statsDao.getCategorySubcategoryBreakdown()

    /**
     * Retrieves active items ranked by cost-per-wear (cheapest per wear first).
     * Excludes items with no purchase price or zero wears.
     * @param fromDate Optional start date (YYYY-MM-DD) to filter wear logs.
     * @return A [Flow] emitting a list of [CostPerWearItem].
     */
    fun getCostPerWear(fromDate: String? = null): Flow<List<CostPerWearItem>> =
        statsDao.getCostPerWear(fromDate)

    /**
     * Counts the total number of logged outfits.
     * @param fromDate Optional start date (YYYY-MM-DD) to restrict the count.
     * @return A [Flow] emitting the total count.
     */
    fun getTotalOutfitsLogged(fromDate: String? = null): Flow<Int> =
        statsDao.getTotalOutfitsLogged(fromDate)

    /**
     * Retrieves a breakdown of wear log entries by category (how many times each category
     * has been worn, not how many items it contains).
     * @param fromDate Optional start date (YYYY-MM-DD) to filter wear logs.
     * @return A [Flow] emitting a list of [BreakdownRow] ordered by wear count descending.
     */
    fun getWearFrequencyByCategory(fromDate: String? = null): Flow<List<BreakdownRow>> =
        statsDao.getWearFrequencyByCategory(fromDate)

    /**
     * Retrieves the list of active items that have never been logged in any outfit.
     * @return A [Flow] emitting a list of [StatItem] ordered alphabetically.
     */
    fun getNeverWornItems(): Flow<List<StatItem>> =
        statsDao.getNeverWornItems()

    /**
     * Retrieves item count per color. Carries the hex value for swatch rendering.
     * @return A [Flow] emitting a list of [ColorBreakdownRow] ordered by count descending.
     */
    fun getColorBreakdown(): Flow<List<ColorBreakdownRow>> =
        statsDao.getBreakdownByColor()

    /**
     * Retrieves item count per occasion.
     * @return A [Flow] emitting a list of [BreakdownRow] ordered by count descending.
     */
    fun getOccasionBreakdown(): Flow<List<BreakdownRow>> =
        statsDao.getBreakdownByOccasion()

    /**
     * Retrieves clean vs dirty item counts. Returns at most 2 rows labelled 'Clean' / 'Dirty'.
     * @return A [Flow] emitting a list of [BreakdownRow].
     */
    fun getWashStatusBreakdown(): Flow<List<BreakdownRow>> =
        statsDao.getWashStatusBreakdown()
}
