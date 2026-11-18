package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.dao.CalendarDay
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.model.OutfitWithItems
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import co.touchlab.kermit.Logger
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.time.AppClock
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = Logger.withTag("JournalViewModel")

/**
 * UI state for the Journal screen.
 *
 * @param currentYearMonth The month currently displayed in the calendar.
 * @param today The current date per the injected clock; drives the "today" marker and the next-month bound.
 * @param calendarDays Days in [currentYearMonth] that have at least one wear log.
 * @param selectedDate The ISO date string ("YYYY-MM-DD") the user has tapped, or null.
 * @param logsForSelectedDate Logs for the currently selected date (drives DayDetailSheet).
 * @param showOutfitPicker When true, the outfit picker sheet is shown instead of DayDetailSheet.
 * @param editingLog Non-null when the log-edit sheet is open for this entry.
 */
data class JournalUiState(
    val currentYearMonth: YearMonth,
    val today: LocalDate,
    val calendarDays: List<CalendarDay> = emptyList(),
    val selectedDate: String? = null,
    val logsForSelectedDate: List<OutfitLogWithMeta> = emptyList(),
    val showOutfitPicker: Boolean = false,
    val editingLog: OutfitLogWithMeta? = null,
    val todayForecast: DailyForecast? = null,
    val forecastDays: List<DailyForecast> = emptyList(),
    val showForecastSheet: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
)

/**
 * ViewModel for the Journal screen.
 *
 * Drives a calendar view showing which days have wear logs and which are OOTD.
 * Month navigation updates [JournalUiState.calendarDays] reactively via [LogRepository].
 * Navigation past the current month is blocked — future logs cannot exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val shortcutUsageReporter: ShortcutUsageReporter,
    private val logRepository: LogRepository,
    private val outfitRepository: OutfitRepository,
    private val imagePathResolver: ImagePathResolver,
    private val weatherRepository: WeatherRepository,
    private val weatherPrefsRepo: WeatherPreferencesRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val initialToday = clock.today()
    private val _currentYearMonth = MutableStateFlow(initialToday.yearMonth)
    private val _selectedDate = MutableStateFlow<String?>(null)
    private val _showOutfitPicker = MutableStateFlow(false)
    private val _editingLog = MutableStateFlow<OutfitLogWithMeta?>(null)
    private val _showForecastSheet = MutableStateFlow(false)

    // Emits today's 7-day forecast whenever weather is enabled; empty list when disabled.
    // Emits emptyList() immediately so combine() is not blocked while the fetch is in-flight.
    private val forecastDays: Flow<List<DailyForecast>> =
        weatherPrefsRepo.getWeatherEnabled().flatMapLatest { enabled ->
            if (!enabled) flowOf(emptyList())
            else flow {
                emit(emptyList())
                when (val result = weatherRepository.getForecast()) {
                    is DataResult.Success -> emit(result.data)
                    is DataResult.Error -> logger.w(result.throwable) { "forecast unavailable" }
                    DataResult.Loading -> Unit
                }
            }
        }

    private val temperatureUnit: Flow<TemperatureUnit> = weatherPrefsRepo.getTemperatureUnit()

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
            today = initialToday,
            calendarDays = days,
            selectedDate = selectedDate,
            logsForSelectedDate = logs,
            showOutfitPicker = showPicker,
        )
    }.combine(_editingLog) { state, editingLog ->
        state.copy(editingLog = editingLog)
    }.combine(forecastDays) { state, forecasts ->
        state.copy(forecastDays = forecasts)
    }.combine(_showForecastSheet) { state, show ->
        state.copy(showForecastSheet = show)
    }.combine(temperatureUnit) { state, unit ->
        // Read the date on every emission so the "Today"/"Tomorrow" labels refresh across midnight.
        val today = clock.today()
        state.copy(
            temperatureUnit = unit,
            today = today,
            todayForecast = state.forecastDays.find { it.date == today },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalUiState(
            currentYearMonth = initialToday.yearMonth,
            today = initialToday,
        ),
    )

    /** All saved outfits — feeds the outfit picker sheet. */
    val outfits: StateFlow<List<OutfitWithItems>> = outfitRepository.getAllOutfitsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun previousMonth() = _currentYearMonth.update { it.minus(1, DateTimeUnit.MONTH) }

    /** Advances to the next month. No-op if already on the current month. */
    fun nextMonth() = _currentYearMonth.update { current ->
        if (current < clock.today().yearMonth) current.plus(1, DateTimeUnit.MONTH) else current
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
        val localDate = parseIsoDateOrNull(date)
        if (localDate == null) {
            logger.w { "navigateToDate: invalid date '$date', ignoring" }
            return
        }
        if (localDate > clock.today()) return
        _currentYearMonth.value = localDate.yearMonth
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
                logger.e(e) { "Failed to toggle OOTD for log $logId" }
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
                logger.e(e) { "Failed to delete log $logId" }
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
            logger.e(e) { "saveLogEdit: unparseable createdAt '${log.createdAt}' for log ${log.id}" }
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
                logger.e(e) { "saveLogEdit: failed to update log ${log.id}" }
                handleActionError(e)
            } finally {
                _editingLog.value = null
            }
        }
    }

    /**
     * Logs [outfitId] as worn on the currently selected date, then closes the picker.
     * Idempotent — no-op if the outfit was already logged for that date.
     *
     * If a cached forecast entry exists for the selected date, its temperature and
     * condition values are pre-populated on the new log. The user can still override
     * them via [openLogEdit].
     */
    fun logOutfitOnDate(outfitId: Long) {
        viewModelScope.launch {
            val date = _selectedDate.value ?: return@launch
            val parsedDate = parseIsoDateOrNull(date) ?: return@launch
            if (parsedDate > clock.today()) return@launch
            val forecast = uiState.value.forecastDays.find { it.date == parsedDate }
            when (val result = logRepository.wearOutfitOnDate(
                outfitId = outfitId,
                date = date,
                temperatureLow = forecast?.tempLow,
                temperatureHigh = forecast?.tempHigh,
                weatherCondition = forecast?.condition,
                precipitationMm = forecast?.precipitationMm,
                windSpeedKmh = forecast?.windSpeedKmh,
            )) {
                is DataResult.Success -> {
                    shortcutUsageReporter.reportUsed("log_fit")
                    _showOutfitPicker.value = false
                }
                is DataResult.Error -> {
                    logger.e(result.throwable) { "logOutfitOnDate: failed to log outfit $outfitId on $date" }
                    handleActionError(result.throwable)
                }
                DataResult.Loading -> Unit
            }
        }
    }

    fun openForecastSheet() { _showForecastSheet.value = true }
    fun closeForecastSheet() { _showForecastSheet.value = false }

    /** Resolves a relative image path to an absolute path for Coil. */
    fun resolveImagePath(path: String?): String? = path?.let { imagePathResolver.absolutePath(it) }

    private fun handleActionError(throwable: Throwable) {
        val error = throwable as? AppError ?: AppError.Unexpected(throwable)
        viewModelScope.launch {
            _actionError.emit(error.asUserMessage())
        }
    }
}
