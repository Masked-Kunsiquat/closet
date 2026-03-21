package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.CalendarDay
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.model.OutfitWithItems
import com.closet.core.data.model.WeatherCondition
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import java.time.Instant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * UI state for the Journal screen.
 *
 * @param currentYearMonth The month currently displayed in the calendar.
 * @param calendarDays Days in [currentYearMonth] that have at least one wear log.
 * @param selectedDate The ISO date string ("YYYY-MM-DD") the user has tapped, or null.
 * @param logsForSelectedDate Logs for the currently selected date (drives DayDetailSheet).
 * @param showOutfitPicker When true, the outfit picker sheet is shown instead of DayDetailSheet.
 * @param editingLog Non-null when the log-edit sheet is open for this entry.
 */
data class JournalUiState(
    val currentYearMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val selectedDate: String? = null,
    val logsForSelectedDate: List<OutfitLogWithMeta> = emptyList(),
    val showOutfitPicker: Boolean = false,
    val editingLog: OutfitLogWithMeta? = null,
)

/**
 * ViewModel for the Journal screen.
 *
 * Drives a calendar view showing which days have wear logs and which are OOTD.
 * Month navigation updates [JournalUiState.calendarDays] reactively via [LogRepository].
 * Navigation past the current month is blocked — future logs cannot exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val outfitRepository: OutfitRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow<String?>(null)
    private val _showOutfitPicker = MutableStateFlow(false)
    private val _editingLog = MutableStateFlow<OutfitLogWithMeta?>(null)

    private val _actionError = MutableSharedFlow<UserMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** One-shot error events emitted when a quick action (toggleOotd, deleteLog) fails. */
    val actionError: SharedFlow<UserMessage> = _actionError.asSharedFlow()

    // Switches to the new month's flow whenever _currentYearMonth changes.
    private val calendarDays = _currentYearMonth
        .flatMapLatest { ym -> logRepository.getCalendarDaysForMonth(ym.toString()) }

    // Switches to the selected date's log list; emits empty list when nothing is selected.
    private val logsForSelectedDate = _selectedDate
        .flatMapLatest { date ->
            if (date != null) logRepository.getLogsByDate(date)
            else flowOf(emptyList())
        }

    val uiState: StateFlow<JournalUiState> = combine(
        _currentYearMonth,
        calendarDays,
        _selectedDate,
        logsForSelectedDate,
        _showOutfitPicker,
    ) { yearMonth, days, selectedDate, logs, showPicker ->
        JournalUiState(
            currentYearMonth = yearMonth,
            calendarDays = days,
            selectedDate = selectedDate,
            logsForSelectedDate = logs,
            showOutfitPicker = showPicker,
        )
    }.combine(_editingLog) { state, editingLog ->
        state.copy(editingLog = editingLog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalUiState(),
    )

    /** All saved outfits — feeds the outfit picker sheet. */
    val outfits: StateFlow<List<OutfitWithItems>> = outfitRepository.getAllOutfitsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun previousMonth() = _currentYearMonth.update { it.minusMonths(1) }

    /** Advances to the next month. No-op if already on the current month. */
    fun nextMonth() = _currentYearMonth.update { current ->
        if (current < YearMonth.now()) current.plusMonths(1) else current
    }

    /** Opens the DayDetailSheet for [date], or pass null to close it. */
    fun selectDate(date: String?) {
        _showOutfitPicker.value = false
        _selectedDate.value = date
    }

    /**
     * Jumps the calendar to [date]'s month and immediately opens the DayDetailSheet for it.
     * Used when deep-linking from the item wear history section.
     */
    fun navigateToDate(date: String) {
        val localDate = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            Timber.w(e, "navigateToDate: invalid date '$date', ignoring")
            return
        }
        if (localDate.isAfter(LocalDate.now())) return
        _currentYearMonth.value = YearMonth.from(localDate)
        _showOutfitPicker.value = false
        _editingLog.value = null
        _selectedDate.value = date
    }

    /**
     * Toggles OOTD for a log entry. If [currentIsOotd] is true, clears the OOTD for that day.
     * Otherwise sets this log as the day's OOTD (atomically clearing any previous one).
     */
    fun toggleOotd(logId: Long, currentIsOotd: Boolean) {
        viewModelScope.launch {
            try {
                val date = _selectedDate.value ?: return@launch
                if (currentIsOotd) logRepository.clearOotd(date)
                else logRepository.setOotd(logId, date)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle OOTD for log $logId")
                handleActionError(e)
            }
        }
    }

    /** Deletes a log entry by ID. */
    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            try {
                logRepository.deleteLog(logId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete log $logId")
                handleActionError(e)
            }
        }
    }

    /** Opens the outfit picker sheet for the currently selected date. */
    fun openOutfitPicker() {
        _showOutfitPicker.value = true
    }

    /** Closes the outfit picker and returns to the day detail sheet. */
    fun dismissOutfitPicker() {
        _showOutfitPicker.value = false
    }

    /** Opens the log-edit sheet for [log]. */
    fun openLogEdit(log: OutfitLogWithMeta) {
        _editingLog.value = log
    }

    /** Closes the log-edit sheet without saving. */
    fun dismissLogEdit() {
        _editingLog.value = null
    }

    /**
     * Saves edits to the current [editingLog]. Preserves all fields not exposed by the editor
     * (outfitId, date, isOotd, createdAt) so only notes and weatherCondition change.
     *
     * Parses [OutfitLogWithMeta.createdAt] before launching the coroutine so a malformed
     * timestamp aborts early without touching the repository or clearing [_editingLog].
     */
    fun saveLogEdit(notes: String?, weatherCondition: WeatherCondition?) {
        val log = _editingLog.value ?: return
        val createdAt = try {
            Instant.parse(log.createdAt)
        } catch (e: Exception) {
            Timber.e(e, "saveLogEdit: unparseable createdAt '${log.createdAt}' for log ${log.id}")
            handleActionError(e)
            return
        }
        viewModelScope.launch {
            try {
                logRepository.updateLog(
                    OutfitLogEntity(
                        id = log.id,
                        outfitId = log.outfitId,
                        date = log.date,
                        isOotd = log.isOotd,
                        notes = notes?.trim()?.ifEmpty { null },
                        weatherCondition = weatherCondition,
                        createdAt = createdAt,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "saveLogEdit: failed to update log ${log.id}")
                handleActionError(e)
            } finally {
                _editingLog.value = null
            }
        }
    }

    /**
     * Logs [outfitId] as worn on the currently selected date, then closes the picker.
     * Idempotent — no-op if the outfit was already logged for that date.
     */
    fun logOutfitOnDate(outfitId: Long) {
        viewModelScope.launch {
            val date = _selectedDate.value ?: return@launch
            val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@launch
            if (parsedDate.isAfter(LocalDate.now())) return@launch
            when (val result = logRepository.wearOutfitOnDate(outfitId, date)) {
                is DataResult.Success -> _showOutfitPicker.value = false
                is DataResult.Error -> {
                    Timber.e(result.throwable, "logOutfitOnDate: failed to log outfit $outfitId on $date")
                    handleActionError(result.throwable)
                }
                DataResult.Loading -> Unit
            }
        }
    }

    /** Resolves a relative image path to an absolute [File] for Coil. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

    private fun handleActionError(throwable: Throwable) {
        val error = throwable as? AppError ?: AppError.Unexpected(throwable)
        viewModelScope.launch {
            _actionError.emit(error.asUserMessage())
        }
    }
}
