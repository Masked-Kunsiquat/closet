package com.closet.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.model.WeatherService
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.util.DataResult
import com.closet.core.data.weather.GoogleWeatherClient
import com.closet.core.data.weather.NwsClient
import com.closet.core.data.weather.OpenMeteoClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The 3-hour cache boundary, read through a [FixedAppClock]. The cache is Google-service with no
 * API key: a fresh cache returns Success without any lookup, a stale one reaches the key check
 * (an error, and no network is touched).
 */
class WeatherRepositoryTest {
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val ttlMs = 3L * 60 * 60 * 1000

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FixedLocation : LocationProvider {
        override suspend fun getLocation(timeoutMs: Long) = 1.0 to 2.0
    }

    private suspend fun forecastWithCacheAge(ageMs: Long): DataResult<*> {
        val prefs = WeatherPreferencesRepository(MemoryStore())
        prefs.setWeatherService(WeatherService.Google)
        prefs.saveCache("[]", now.toEpochMilliseconds() - ageMs, 1.0, 2.0, WeatherService.Google)
        val http = HttpClient(OkHttp)
        try {
            val clock = FixedAppClock(now)
            return WeatherRepository(
                OpenMeteoClient(http), NwsClient(http), GoogleWeatherClient(http, clock),
                prefs, FixedLocation(), Json, clock,
            ).getForecast()
        } finally {
            http.close()
        }
    }

    @Test fun cacheOneMillisecondInsideTheLimitIsFresh() = runBlocking {
        assertTrue(forecastWithCacheAge(ttlMs - 1) is DataResult.Success)
    }

    @Test fun cacheExactlyAtTheLimitIsStale() = runBlocking {
        assertTrue(forecastWithCacheAge(ttlMs) is DataResult.Error)
    }

    @Test fun cacheOneMillisecondPastTheLimitIsStale() = runBlocking {
        assertTrue(forecastWithCacheAge(ttlMs + 1) is DataResult.Error)
    }
}
