package com.closet.core.data.repository

import com.closet.core.data.location.LocationProvider
import com.closet.core.data.model.CachedForecastEntry
import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.WeatherService
import com.closet.core.data.model.toCached
import com.closet.core.data.model.toDomain
import com.closet.core.data.weather.GoogleWeatherClient
import com.closet.core.data.weather.NwsClient
import com.closet.core.data.weather.OpenMeteoClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Cache is considered fresh for this many milliseconds after it was written. */
private const val FORECAST_CACHE_TTL_MS = 3L * 60 * 60 * 1000   // 3 hours

/**
 * Orchestrates weather forecast retrieval with a 3-hour DataStore cache.
 *
 * On each [getForecast] call:
 * 1. If a cached forecast exists and is < [FORECAST_CACHE_TTL_MS] old, return it.
 * 2. Otherwise, get the device location via [LocationProvider] (falls back to the
 *    last-cached lat/lon if the device has no fix).
 * 3. Delegate to the active [WeatherService] client.
 * 4. On success, serialize the result and write it to DataStore.
 * 5. On failure, return cached data if present; otherwise propagate the error.
 *
 * [GoogleWeatherClient] is injected as a concrete type (not via the
 * [com.closet.core.data.weather.WeatherServiceClient] interface) because it
 * requires an API key parameter that the interface contract doesn't carry.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val openMeteoClient: OpenMeteoClient,
    private val nwsClient: NwsClient,
    private val googleClient: GoogleWeatherClient,
    private val weatherPrefsRepo: WeatherPreferencesRepository,
    private val locationProvider: LocationProvider,
    private val json: Json,
) {
    /**
     * Returns a 7-day forecast (today + 6 days), respecting the 3-hour cache.
     * Always succeeds with cached data when a fresh fetch fails and a cache exists.
     */
    suspend fun getForecast(): Result<List<DailyForecast>> {
        val cachedJson = weatherPrefsRepo.getCachedForecastJson().first()
        val cachedTimestamp = weatherPrefsRepo.getCachedForecastTimestamp().first()
        val age = System.currentTimeMillis() - cachedTimestamp

        if (cachedJson.isNotEmpty() && age < FORECAST_CACHE_TTL_MS) {
            return parseCached(cachedJson)
                .onFailure { Timber.w(it, "WeatherRepository: stale cache unreadable, fetching fresh") }
                .recoverCatching { fetchFresh().getOrThrow() }
        }

        return fetchFresh().recoverCatching { error ->
            // Fetch failed — return stale cache rather than an error if we have one.
            if (cachedJson.isNotEmpty()) {
                Timber.w(error, "WeatherRepository: fetch failed, returning stale cache")
                parseCached(cachedJson).getOrThrow()
            } else {
                throw error
            }
        }
    }

    private fun parseCached(json: String): Result<List<DailyForecast>> = runCatching {
        this.json.decodeFromString<List<CachedForecastEntry>>(json).map { it.toDomain() }
    }

    private suspend fun fetchFresh(): Result<List<DailyForecast>> {
        // ── 1. Resolve location ───────────────────────────────────────────────
        val location = locationProvider.getLocation()
            ?: run {
                val cachedLat = weatherPrefsRepo.getCachedLatitude().first()
                val cachedLon = weatherPrefsRepo.getCachedLongitude().first()
                if (cachedLat != 0.0 || cachedLon != 0.0) Pair(cachedLat, cachedLon) else null
            }
            ?: return Result.failure(IllegalStateException("No location available — grant location permission and try again."))

        val (lat, lon) = location

        // ── 2. Select client ──────────────────────────────────────────────────
        val service = weatherPrefsRepo.getWeatherService().first()
        if (service == WeatherService.Google) {
            val key = weatherPrefsRepo.getGoogleApiKey().first()
            if (key.isBlank()) {
                return Result.failure(IllegalStateException("Google Weather API key is not set. Add it in Settings → Weather."))
            }
        }

        // ── 3. Fetch ──────────────────────────────────────────────────────────
        val result = when (service) {
            WeatherService.OpenMeteo -> openMeteoClient.fetchDailyForecast(lat, lon)
            WeatherService.Nws -> nwsClient.fetchDailyForecast(lat, lon)
            WeatherService.Google -> {
                val key = weatherPrefsRepo.getGoogleApiKey().first()
                googleClient.fetchDailyForecast(lat, lon, key)
            }
        }

        // ── 4. Cache on success ───────────────────────────────────────────────
        result.onSuccess { forecasts ->
            runCatching {
                val entriesJson = json.encodeToString(forecasts.map { it.toCached() })
                weatherPrefsRepo.saveCache(entriesJson, System.currentTimeMillis(), lat, lon)
            }.onFailure { Timber.w(it, "WeatherRepository: failed to write forecast cache") }
        }

        return result
    }
}
