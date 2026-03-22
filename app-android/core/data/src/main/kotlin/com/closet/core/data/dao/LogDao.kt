package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for outfit logging and calendar management.
 * Parity: This is the native equivalent of OutfitLog queries from queries.ts.
 */
@Dao
interface LogDao {

    /**
     * Retrieves all outfit logs for a specific date, including metadata like outfit name and item count.
     * @param date The date string (YYYY-MM-DD).
     * @return A [Flow] emitting a list of [OutfitLogWithMeta].
     */
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

    /**
     * Returns the row ID of an existing log for [outfitId] on [date], or null if none exists.
     * Used by [LogRepository.wearOutfitToday] to keep wear logging idempotent.
     */
    @Query("SELECT id FROM outfit_logs WHERE outfit_id = :outfitId AND date = :date LIMIT 1")
    suspend fun getLogIdByOutfitAndDate(outfitId: Long, date: String): Long?

    /**
     * Inserts a new outfit log entry.
     * @param log The [OutfitLogEntity] to insert.
     * @return The row ID of the newly inserted log.
     */
    @Insert
    suspend fun insertLog(log: OutfitLogEntity): Long

    /**
     * Updates an existing outfit log entry.
     * @param log The [OutfitLogEntity] with updated values.
     */
    @Update
    suspend fun updateLog(log: OutfitLogEntity)

    /**
     * Deletes a specific outfit log entry.
     * @param log The [OutfitLogEntity] to delete.
     */
    @Delete
    suspend fun deleteLog(log: OutfitLogEntity)

    /**
     * Deletes an outfit log entry by its ID.
     * @param logId The ID of the log entry.
     */
    @Query("DELETE FROM outfit_logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)

    /**
     * Atomically sets a log entry as the Outfit of the Day (OOTD) for its date.
     * Clears any existing OOTD for that date before marking the new one.
     * @param logId The ID of the log entry to mark as OOTD.
     * @param date The date for which to set the OOTD.
     */
    @Transaction
    suspend fun setOotd(logId: Long, date: String) {
        clearOotdForDate(date)
        markOotd(logId)
    }

    /**
     * Clears the OOTD flag for all log entries on a specific date.
     * @param date The date string.
     */
    @Query("UPDATE outfit_logs SET is_ootd = 0 WHERE date = :date AND is_ootd = 1")
    suspend fun clearOotdForDate(date: String)

    /**
     * Marks a specific log entry as the OOTD.
     * @param logId The ID of the log entry.
     */
    @Query("UPDATE outfit_logs SET is_ootd = 1 WHERE id = :logId")
    suspend fun markOotd(logId: Long)

    /**
     * Retrieves a summary of calendar days within a range, indicating log counts and OOTD status.
     * Using BETWEEN allows SQLite to use the index on the date column.
     * @param startDate The start date of the range (YYYY-MM-DD).
     * @param endDate The end date of the range (YYYY-MM-DD).
     * @return A [Flow] emitting a list of [CalendarDay].
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

    /**
     * Retrieves all logs in which a specific clothing item was worn, most recent first.
     * Joins outfit_logs → outfit_log_items snapshot (instead of the live outfit_items table)
     * so that retroactive outfit edits do not alter historical wear records.
     * @param clothingItemId The ID of the clothing item.
     * @return A [Flow] emitting a list of [ItemWearLog].
     */
    @Query("""
        SELECT
            ol.id,
            ol.date,
            ol.is_ootd,
            oli.outfit_name
        FROM outfit_logs ol
        JOIN outfit_log_items oli ON oli.outfit_log_id = ol.id
                                 AND oli.clothing_item_id = :clothingItemId
        ORDER BY ol.date DESC
    """)
    fun getLogsForItem(clothingItemId: Long): Flow<List<ItemWearLog>>

    /**
     * Inserts snapshot rows for all items currently in an outfit into [outfit_log_items].
     * Snapshots the outfit name at call time so renames don't affect history.
     * Uses INSERT OR IGNORE to be idempotent (safe for the idempotency re-check path).
     */
    @Query("""
        INSERT OR IGNORE INTO outfit_log_items (outfit_log_id, clothing_item_id, outfit_name)
        SELECT :logId, oi.clothing_item_id, o.name
        FROM outfit_items oi
        LEFT JOIN outfits o ON o.id = oi.outfit_id
        WHERE oi.outfit_id = :outfitId
    """)
    suspend fun insertSnapshotRows(logId: Long, outfitId: Long)

    /**
     * Atomically inserts an outfit log and immediately snapshots its item membership.
     * If [log.outfitId] is null (free-form log) no snapshot rows are written.
     * @return The row ID of the newly inserted log.
     */
    @Transaction
    suspend fun insertLogAndSnapshot(log: OutfitLogEntity): Long {
        val logId = insertLog(log)
        if (log.outfitId != null) {
            insertSnapshotRows(logId, log.outfitId)
        }
        return logId
    }
}

/**
 * Representation of an outfit log entry with calculated metadata.
 * Parity: This is the native equivalent of OutfitLogWithMeta from types.ts.
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
 * Summary of a single calendar day's logging activity.
 * Parity: This is the native equivalent of CalendarDay from types.ts.
 */
data class CalendarDay(
    val date: String,
    @ColumnInfo(name = "log_count") val logCount: Int,
    @ColumnInfo(name = "has_ootd") val hasOotd: Int
)

/**
 * A single wear-history entry for a clothing item — shows when and in which outfit it was worn.
 * Returned by [LogDao.getLogsForItem].
 */
data class ItemWearLog(
    val id: Long,
    val date: String,
    @ColumnInfo(name = "is_ootd") val isOotd: Int,
    @ColumnInfo(name = "outfit_name") val outfitName: String?,
)
