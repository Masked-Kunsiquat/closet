package com.closet.core.data.repository

import com.closet.core.data.dao.CalendarDay
import com.closet.core.data.dao.LogDao
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.OutfitLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Outfit Log (Journal) operations.
 * Manages the history of what was worn and when, including weather data and notes.
 * Parity: This is the native equivalent of Outfit logs section in hangr/db/queries.ts.
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao
) {
    /**
     * Retrieves all outfit logs for a specific date (e.g., "2024-03-20").
     * @param date The date string in YYYY-MM-DD format.
     * @return A [Flow] emitting a list of [OutfitLogWithMeta].
     */
    fun getLogsByDate(date: String): Flow<List<OutfitLogWithMeta>> = 
        logDao.getLogsByDate(date)

    /**
     * Creates a new outfit log entry.
     * @param log The [OutfitLogEntity] to insert.
     * @return The row ID of the newly created log.
     */
    suspend fun insertLog(log: OutfitLogEntity): Long = 
        logDao.insertLog(log)

    /**
     * Updates an existing log's metadata (e.g., notes, weather, temperature).
     * @param log The [OutfitLogEntity] with updated values.
     */
    suspend fun updateLog(log: OutfitLogEntity) = 
        logDao.updateLog(log)

    /**
     * Deletes an outfit log entry by its unique ID.
     * @param logId The ID of the log entry to remove.
     */
    suspend fun deleteLog(logId: Long) {
        logDao.deleteLogById(logId)
    }

    /**
     * Marks a specific log entry as the "Outfit of the Day" (OOTD) for its date.
     * This is an atomic operation that clears any existing OOTD for that date.
     * @param logId The ID of the log entry.
     * @param date The date string (YYYY-MM-DD).
     */
    suspend fun setOotd(logId: Long, date: String) = 
        logDao.setOotd(logId, date)

    /**
     * Clears the "Outfit of the Day" status for all logs on a specific date.
     * @param date The date string (YYYY-MM-DD).
     */
    suspend fun clearOotd(date: String) = 
        logDao.clearOotdForDate(date)

    /**
     * Retrieves a monthly summary of logged days for the calendar view.
     * @param yearMonth The year and month string in YYYY-MM format.
     * @return A [Flow] emitting a list of [CalendarDay] summary objects.
     */
    fun getCalendarDaysForMonth(yearMonth: String): Flow<List<CalendarDay>> {
        val startDate = "${yearMonth}-01"
        val endDate = "${yearMonth}-31" // SQLite handles 31 safely even for shorter months
        return logDao.getCalendarDaysInRange(startDate, endDate)
    }
}
