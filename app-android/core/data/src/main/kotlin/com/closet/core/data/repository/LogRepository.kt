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
 * Mirror of Outfit logs section in hangr/db/queries.ts.
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao
) {
    /**
     * Fetch all logs for a specific date (e.g. "2024-03-20").
     */
    fun getLogsByDate(date: String): Flow<List<OutfitLogWithMeta>> = 
        logDao.getLogsByDate(date)

    /**
     * Create a new outfit log.
     */
    suspend fun insertLog(log: OutfitLogEntity): Long = 
        logDao.insertLog(log)

    /**
     * Update an existing log's notes/weather.
     */
    suspend fun updateLog(log: OutfitLogEntity) = 
        logDao.updateLog(log)

    /**
     * Delete a log by ID.
     */
    suspend fun deleteLog(logId: Long) {
        logDao.deleteLog(OutfitLogEntity(id = logId, date = "")) // date is unused by delete
    }

    /**
     * Mark a log as the "Outfit of the Day" for its date.
     * Parity: Atomic swap using @Transaction in DAO.
     */
    suspend fun setOotd(logId: Long, date: String) = 
        logDao.setOotd(logId, date)

    /**
     * Clear OOTD status for a date.
     */
    suspend fun clearOotd(date: String) = 
        logDao.clearOotdForDate(date)

    /**
     * Monthly summary for the calendar view.
     * yearMonth format: "2024-03"
     */
    fun getCalendarDaysForMonth(yearMonth: String): Flow<List<CalendarDay>> = 
        logDao.getCalendarDaysForMonth(yearMonth)
}
