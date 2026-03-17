package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class StatsOverview(
    val totalItems: Int,
    val wornItems: Int,
    val neverWornItems: Int,
    val totalValue: Double?
)

data class StatItem(
    val id: Long,
    val name: String,
    val imagePath: String?,
    val wearCount: Int
)

data class BreakdownRow(
    val label: String,
    val count: Int
)

@Dao
interface StatsDao {

    /**
     * Parity: Mirror of getStatsOverview in queries.ts.
     * Calculates totals and worn/never-worn counts for Active items.
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
     * Parity: Mirror of getMostWornItems in queries.ts.
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
    fun getMostWornItems(fromDate: String?, limit: Int = 15): Flow<List<StatItem>>

    /**
     * Parity: Mirror of getBreakdownByCategory in queries.ts.
     */
    @Query("""
        SELECT c.name AS label, COUNT(DISTINCT ci.id) AS count
        FROM clothing_items ci
        JOIN categories c ON c.id = ci.category_id
        WHERE ci.status = 'Active'
        GROUP BY c.id
        ORDER BY count DESC
    """)
    fun getBreakdownByCategory(): Flow<List<BreakdownRow>>
}
