package com.closet.features.outfits

import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toKotlinLocalDate
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Forecast lookups in [JournalViewModel] match core/data's kotlinx [DailyForecast.date]
 * against the feature's java.time dates. These pin that the two are compared in one type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val todayJava = java.time.LocalDate.now()
    private val todayForecast = DailyForecast(
        date = todayJava.toKotlinLocalDate(),
        tempLow = 4.0,
        tempHigh = 12.5,
        condition = WeatherCondition.Rainy,
        precipitationMm = 3.2,
        windSpeedKmh = 18.0,
        uvIndex = 2,
        humidity = 80,
    )
    private val tomorrowForecast = todayForecast.copy(
        date = todayJava.plusDays(1).toKotlinLocalDate(),
        tempLow = 20.0,
        tempHigh = 30.0,
        condition = WeatherCondition.Sunny,
    )

    private val logRepository = mockk<LogRepository>(relaxed = true)
    private val outfitRepository = mockk<OutfitRepository>()
    private val reporter = mockk<ShortcutUsageReporter>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>()
    private val weatherPrefsRepo = mockk<WeatherPreferencesRepository>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { logRepository.getCalendarDaysForMonth(any()) } returns flowOf(emptyList())
        every { logRepository.getLogsByDate(any()) } returns flowOf(emptyList())
        coEvery {
            logRepository.wearOutfitOnDate(any(), any(), any(), any(), any(), any(), any())
        } returns DataResult.Success(1L)
        every { outfitRepository.getAllOutfitsWithItems() } returns flowOf(emptyList())
        every { weatherPrefsRepo.getWeatherEnabled() } returns flowOf(true)
        every { weatherPrefsRepo.getTemperatureUnit() } returns flowOf(TemperatureUnit.Celsius)
        coEvery { weatherRepository.getForecast() } returns
            DataResult.Success(listOf(todayForecast, tomorrowForecast))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = JournalViewModel(
        shortcutUsageReporter = reporter,
        logRepository = logRepository,
        outfitRepository = outfitRepository,
        imagePathResolver = ImagePathResolver { "/root/$it" },
        weatherRepository = weatherRepository,
        weatherPrefsRepo = weatherPrefsRepo,
    )

    @Test
    fun `todayForecast is the forecast dated today`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }

        assertEquals(todayForecast, vm.uiState.value.todayForecast)
    }

    @Test
    fun `logOutfitOnDate passes the selected day's forecast weather through`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }

        vm.selectDate(todayJava.toString())
        vm.logOutfitOnDate(outfitId = 7L)

        coVerify {
            logRepository.wearOutfitOnDate(
                outfitId = 7L,
                date = todayJava.toString(),
                temperatureLow = 4.0,
                temperatureHigh = 12.5,
                weatherCondition = WeatherCondition.Rainy,
                precipitationMm = 3.2,
                windSpeedKmh = 18.0,
            )
        }
    }

    @Test
    fun `successful log reports the log_fit shortcut`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        vm.selectDate(todayJava.toString())
        vm.logOutfitOnDate(outfitId = 7L)
        verify { reporter.reportUsed("log_fit") }
    }

    @Test
    fun `failed log does not report the shortcut`() = runTest {
        coEvery {
            logRepository.wearOutfitOnDate(any(), any(), any(), any(), any(), any(), any())
        } returns DataResult.Error(RuntimeException("boom"))
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        vm.selectDate(todayJava.toString())
        vm.logOutfitOnDate(outfitId = 7L)
        verify(exactly = 0) { reporter.reportUsed(any()) }
    }

    @Test
    fun `logging on a future date does not call the repository`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        vm.selectDate(todayJava.plusDays(1).toString())
        vm.logOutfitOnDate(outfitId = 7L)
        coVerify(exactly = 0) {
            logRepository.wearOutfitOnDate(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `navigateToDate with an invalid date leaves the month unchanged`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        val before = vm.uiState.value.currentYearMonth
        vm.navigateToDate("not-a-date")
        assertEquals(before, vm.uiState.value.currentYearMonth)
    }

    @Test
    fun `navigateToDate with a past date jumps to its month`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        vm.navigateToDate("2024-03-09")
        assertEquals(YearMonth(2024, 3), vm.uiState.value.currentYearMonth)
        assertEquals("2024-03-09", vm.uiState.value.selectedDate)
    }

    @Test
    fun `nextMonth at the current month stays put`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        val before = vm.uiState.value.currentYearMonth
        vm.nextMonth()
        assertEquals(before, vm.uiState.value.currentYearMonth)
    }
}
