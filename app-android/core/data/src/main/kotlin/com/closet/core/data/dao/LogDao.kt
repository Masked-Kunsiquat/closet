package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Mirror of OutfitLog queries from queries.ts.
 */
@Dao
interface LogDao {

    @Query("""
        SELECT
            ol.*,
            o.name AS outfit_name,
            COUNT(oi.clothing_item_id) AS item_count,
            (SELECT ci.image_path
             FROM outfit_items oi2
             JOIN clothing_items ci ON ci.id = oi2.clothing_item_id
             WHERE oi2.outfit_id = ol.outfit_id AND ci.image_path IS NOT NULL
             LIMIT 1) AS cover_image
        FROM outfit_logs ol
        LEFT JOIN outfits o     ON o.id  = ol.outfit_id
        LEFT JOIN outfit_items oi ON oi.outfit_id = ol.outfit_id
        WHERE ol.date = :date
        GROUP BY ol.id
        ORDER BY ol.is_ootd DESC, ol.created_at ASC
    """)
    fun getLogsByDate(date: String): Flow<List<OutfitLogWithMeta>>

    @Insert
    suspend fun insertLog(log: OutfitLogEntity): Long

    @Update
    suspend fun updateLog(log: OutfitLogEntity)

    @Delete
    suspend fun deleteLog(log: OutfitLogEntity)

    @Query("DELETE FROM outfit_logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)

    @Transaction
    suspend fun setOotd(logId: Long, date: String) {
        clearOotdForDate(date)
        markOotd(logId)
    }

    @Query("UPDATE outfit_logs SET is_ootd = 0 WHERE date = :date AND is_ootd = 1")
    suspend fun clearOotdForDate(date: String)

    @Query("UPDATE outfit_logs SET is_ootd = 1 WHERE id = :logId")
    suspend fun markOotd(logId: Long)

    /**
     * Using BETWEEN allows SQLite to use the index on the date column.
     * startDate: "YYYY-MM-01"
     * endDate: "YYYY-MM-31" (or last day of month)
     */
    @Query("""
        SELECT
          date,
          COUNT(*)                  AS log_count,
          MAX(is_ootd)              AS has_ootd
        FROM outfit_logs
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date
    """)
    fun getCalendarDaysInRange(startDate: String, endDate: String): Flow<List<CalendarDay>>
}

/**
 * Mirror of OutfitLogWithMeta from types.ts.
 */
data class OutfitLogWithMeta(
    val id: Long,
    @ColumnInfo(name = "outfit_id") val outfitId: Long?,
    val date: String,
    @ColumnInfo(name = "is_ootd") val isOotd: Int,
    val notes: String?,
    @ColumnInfo(name = "temperature_low") val temperatureLow: Double?,
    @ColumnInfo(name = "temperature_high") val temperatureHigh: Double?,
    @ColumnInfo(name = "weather_condition") val weatherCondition: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "outfit_name") val outfitName: String?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "cover_image") val coverImage: String?
)

/**
 * Mirror of CalendarDay from types.ts.
 */
data class CalendarDay(
    val date: String,
    @ColumnInfo(name = "log_count") val logCount: Int,
    @ColumnInfo(name = "has_ootd") val hasOotd: Int
)
