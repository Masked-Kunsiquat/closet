package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * High-level overview of wardrobe statistics.
 */
data class StatsOverview(
    val totalItems: Int,
    val wornItems: Int,
    val neverWornItems: Int,
    val totalValue: Double?
)

/**
 * Represents a single clothing item in a statistics list (e.g., most worn).
 */
data class StatItem(
    val id: Long,
    val name: String,
    val imagePath: String?,
    val wearCount: Int
)

/**
 * Represents a row in a statistical breakdown (e.g., by category).
 */
data class BreakdownRow(
    val label: String,
    val count: Int
)

/**
 * Represents a clothing item ranked by cost-per-wear (cheapest per wear first).
 * Only items with a known purchase price and at least one wear are included.
 */
data class CostPerWearItem(
    val id: Long,
    val name: String,
    val imagePath: String?,
    val purchasePrice: Double,
    val wearCount: Int,
    val costPerWear: Double
)

/**
 * Data Access Object for generating wardrobe and usage statistics.
 * Parity: This is the native equivalent of various stats-related queries from queries.ts.
 */
@Dao
interface StatsDao {

    /**
     * Calculates a high-level overview of the wardrobe, including total item count,
     * worn vs. never-worn counts, and total monetary value.
     * Only considers items with 'Active' status.
     * @param fromDate Optional start date to filter usage (YYYY-MM-DD).
     * @return A [Flow] emitting the [StatsOverview].
     */
    @Query("""
        SELECT
            COUNT(*) AS totalItems,
            COUNT(CASE WHEN EXISTS (
                SELECT 1 FROM outfit_logs ol
                JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
                WHERE oi.clothing_item_id = ci.id
                  AND (:fromDate IS NULL OR ol.date >= :fromDate)
            ) THEN 1 END) AS wornItems,
            COUNT(CASE WHEN NOT EXISTS (
                SELECT 1 FROM outfit_logs ol
                JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
                WHERE oi.clothing_item_id = ci.id
                  AND (:fromDate IS NULL OR ol.date >= :fromDate)
            ) THEN 1 END) AS neverWornItems,
            SUM(purchase_price) AS totalValue
        FROM clothing_items ci
        WHERE ci.status = 'Active'
    """)
    fun getStatsOverview(fromDate: String?): Flow<StatsOverview>

    /**
     * Retrieves the most frequently worn items within an optional date range.
     * @param fromDate Optional start date to filter logs.
     * @param limit The maximum number of items to return.
     * @return A [Flow] emitting a list of [StatItem] ordered by wear count.
     */
    @Query("""
        SELECT
            ci.id,
            ci.name,
            ci.image_path AS imagePath,
            COUNT(DISTINCT ol.id) AS wearCount
        FROM clothing_items ci
        JOIN outfit_items oi ON oi.clothing_item_id = ci.id
        JOIN outfit_logs ol  ON ol.outfit_id = oi.outfit_id
        WHERE ci.status = 'Active'
          AND (:fromDate IS NULL OR ol.date >= :fromDate)
        GROUP BY ci.id
        ORDER BY wearCount DESC
        LIMIT :limit
    """)
    fun getMostWornItems(fromDate: String?, limit: Int): Flow<List<StatItem>>

    /**
     * Generates a breakdown of active items by category.
     * @return A [Flow] emitting a list of [BreakdownRow] showing item counts per category.
     */
    @Query("""
        SELECT COALESCE(c.name, 'Uncategorized') AS label, COUNT(DISTINCT ci.id) AS count
        FROM clothing_items ci
        LEFT JOIN categories c ON c.id = ci.category_id
        WHERE ci.status = 'Active'
        GROUP BY label
        ORDER BY count DESC
    """)
    fun getBreakdownByCategory(): Flow<List<BreakdownRow>>

    /**
     * Ranks active items by cost-per-wear (cheapest per wear first).
     * Only includes items that have a known purchase price and at least one wear log.
     * @param fromDate Optional start date (YYYY-MM-DD) to filter wear logs.
     * @return A [Flow] emitting a list of [CostPerWearItem] ordered ascending by cost-per-wear.
     */
    @Query("""
        SELECT
            ci.id,
            ci.name,
            ci.image_path AS imagePath,
            ci.purchase_price AS purchasePrice,
            COUNT(DISTINCT ol.id) AS wearCount,
            ci.purchase_price / COUNT(DISTINCT ol.id) AS costPerWear
        FROM clothing_items ci
        JOIN outfit_items oi ON oi.clothing_item_id = ci.id
        JOIN outfit_logs ol  ON ol.outfit_id = oi.outfit_id
        WHERE ci.status = 'Active'
          AND ci.purchase_price IS NOT NULL
          AND (:fromDate IS NULL OR ol.date >= :fromDate)
        GROUP BY ci.id
        HAVING wearCount > 0
        ORDER BY costPerWear ASC
    """)
    fun getCostPerWear(fromDate: String?): Flow<List<CostPerWearItem>>

    /**
     * Counts the total number of distinct outfit log entries.
     * @param fromDate Optional start date (YYYY-MM-DD) to restrict the count.
     * @return A [Flow] emitting the total count of logged outfits.
     */
    @Query("""
        SELECT COUNT(DISTINCT id)
        FROM outfit_logs
        WHERE :fromDate IS NULL OR date >= :fromDate
    """)
    fun getTotalOutfitsLogged(fromDate: String?): Flow<Int>

    /**
     * Generates a breakdown of wear log entries by category.
     * Counts how many times items in each category have been worn, rather than how many items
     * exist per category. Joins through outfit_items → outfit_logs.
     * @param fromDate Optional start date (YYYY-MM-DD) to filter wear logs.
     * @return A [Flow] emitting a list of [BreakdownRow] ordered by wear count descending.
     */
    @Query("""
        SELECT COALESCE(c.name, 'Uncategorized') AS label, COUNT(DISTINCT ol.id) AS count
        FROM clothing_items ci
        LEFT JOIN categories c ON c.id = ci.category_id
        JOIN outfit_items oi ON oi.clothing_item_id = ci.id
        JOIN outfit_logs ol  ON ol.outfit_id = oi.outfit_id
        WHERE ci.status = 'Active'
          AND (:fromDate IS NULL OR ol.date >= :fromDate)
        GROUP BY label
        ORDER BY count DESC
    """)
    fun getWearFrequencyByCategory(fromDate: String?): Flow<List<BreakdownRow>>

    /**
     * Returns the list of active items that have never appeared in any outfit log.
     * The [StatItem.wearCount] will always be 0 for every row returned.
     * @return A [Flow] emitting a list of [StatItem] ordered alphabetically by name.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path AS imagePath, 0 AS wearCount
        FROM clothing_items ci
        WHERE ci.status = 'Active'
          AND NOT EXISTS (
              SELECT 1 FROM outfit_logs ol
              JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
              WHERE oi.clothing_item_id = ci.id
          )
        ORDER BY ci.name ASC
    """)
    fun getNeverWornItems(): Flow<List<StatItem>>
}
