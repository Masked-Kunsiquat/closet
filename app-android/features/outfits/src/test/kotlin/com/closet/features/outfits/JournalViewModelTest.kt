package com.closet.features.outfits

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toKotlinLocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
    private val storageRepository = mockk<StorageRepository>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>()
    private val weatherPrefsRepo = mockk<WeatherPreferencesRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.reportShortcutUsed(any(), any()) } returns Unit
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

    @After
    fun tearDown() {
        unmockkStatic(ShortcutManagerCompat::class)
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = JournalViewModel(
        appContext = mockk<Context>(relaxed = true),
        logRepository = logRepository,
        outfitRepository = outfitRepository,
        storageRepository = storageRepository,
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
}
