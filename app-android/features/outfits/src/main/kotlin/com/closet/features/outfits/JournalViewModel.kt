package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.CalendarDay
import com.closet.core.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

/**
 * UI state for the Journal screen.
 *
 * @param currentYearMonth The month currently displayed in the calendar.
 * @param calendarDays Days in [currentYearMonth] that have at least one wear log.
 * @param selectedDate The ISO date string ("YYYY-MM-DD") the user has tapped, or null.
 */
data class JournalUiState(
    val currentYearMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val selectedDate: String? = null,
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
    private val logRepository: LogRepository
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow<String?>(null)

    // Switches to the new month's flow whenever _currentYearMonth changes.
    private val calendarDays = _currentYearMonth
        .flatMapLatest { ym -> logRepository.getCalendarDaysForMonth(ym.toString()) }

    val uiState: StateFlow<JournalUiState> = combine(
        _currentYearMonth,
        calendarDays,
        _selectedDate
    ) { yearMonth, days, selectedDate ->
        JournalUiState(
            currentYearMonth = yearMonth,
            calendarDays = days,
            selectedDate = selectedDate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalUiState(),
    )

    fun previousMonth() = _currentYearMonth.update { it.minusMonths(1) }

    /** Advances to the next month. No-op if already on the current month. */
    fun nextMonth() = _currentYearMonth.update { current ->
        if (current < YearMonth.now()) current.plusMonths(1) else current
    }

    /**
     * Sets the selected date. Pass null to deselect.
     * Phase 3 (DayDetailSheet) will react to non-null values.
     */
    fun selectDate(date: String?) {
        _selectedDate.value = date
    }
}
