package com.closet.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_prefs")

/**
 * Repository for persisting weather feature preferences via [DataStore].
 *
 * Backed by a separate DataStore file (`weather_prefs`) from the main app prefs,
 * keeping weather/network concerns isolated in [com.closet.core.data].
 *
 * All temperature values stored in the DB are canonical °C. [TemperatureUnit]
 * controls display conversion only — never stored values.
 *
 * Provided as a [Singleton] — inject anywhere via Hilt.
 */
@Singleton
class WeatherPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val weatherEnabledKey = booleanPreferencesKey("weather_enabled")
    private val weatherServiceKey = stringPreferencesKey("weather_service")
    private val googleApiKeyKey = stringPreferencesKey("google_api_key")
    private val temperatureUnitKey = stringPreferencesKey("temperature_unit")
    private val cachedForecastJsonKey = stringPreferencesKey("cached_forecast_json")
    private val cachedForecastTimestampKey = longPreferencesKey("cached_forecast_timestamp")
    private val cachedLatitudeKey = doublePreferencesKey("cached_latitude")
    private val cachedLongitudeKey = doublePreferencesKey("cached_longitude")
    private val cachedForecastServiceKey = stringPreferencesKey("cached_forecast_service")

    // ── Weather enabled ──────────────────────────────────────────────────────

    fun getWeatherEnabled(): Flow<Boolean> = context.weatherDataStore.data.map { prefs ->
        prefs[weatherEnabledKey] ?: false
    }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        context.weatherDataStore.edit { prefs ->
            prefs[weatherEnabledKey] = enabled
        }
    }

    // ── Service selection ────────────────────────────────────────────────────

    fun getWeatherService(): Flow<WeatherService> = context.weatherDataStore.data.map { prefs ->
        WeatherService.fromString(prefs[weatherServiceKey] ?: WeatherService.OpenMeteo.name)
    }

    suspend fun setWeatherService(service: WeatherService) {
        context.weatherDataStore.edit { prefs ->
            prefs[weatherServiceKey] = service.name
        }
    }

    // ── Google API key ───────────────────────────────────────────────────────

    fun getGoogleApiKey(): Flow<String> = context.weatherDataStore.data.map { prefs ->
        prefs[googleApiKeyKey] ?: ""
    }

    suspend fun setGoogleApiKey(key: String) {
        context.weatherDataStore.edit { prefs ->
            prefs[googleApiKeyKey] = key
        }
    }

    // ── Temperature unit ─────────────────────────────────────────────────────

    fun getTemperatureUnit(): Flow<TemperatureUnit> = context.weatherDataStore.data.map { prefs ->
        TemperatureUnit.fromString(prefs[temperatureUnitKey] ?: TemperatureUnit.Celsius.name)
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.weatherDataStore.edit { prefs ->
            prefs[temperatureUnitKey] = unit.name
        }
    }

    // ── Forecast cache ───────────────────────────────────────────────────────

    fun getCachedForecastJson(): Flow<String> = context.weatherDataStore.data.map { prefs ->
        prefs[cachedForecastJsonKey] ?: ""
    }

    fun getCachedForecastTimestamp(): Flow<Long> = context.weatherDataStore.data.map { prefs ->
        prefs[cachedForecastTimestampKey] ?: 0L
    }

    fun getCachedLatitude(): Flow<Double> = context.weatherDataStore.data.map { prefs ->
        prefs[cachedLatitudeKey] ?: 0.0
    }

    fun getCachedLongitude(): Flow<Double> = context.weatherDataStore.data.map { prefs ->
        prefs[cachedLongitudeKey] ?: 0.0
    }

    /** Returns the [WeatherService] that produced the current cached forecast, or null if no cache. */
    fun getCachedForecastService(): Flow<WeatherService?> = context.weatherDataStore.data.map { prefs ->
        prefs[cachedForecastServiceKey]?.let { WeatherService.fromString(it) }
    }

    /**
     * Writes all cache fields atomically in a single DataStore transaction.
     * Call this after a successful forecast fetch.
     */
    suspend fun saveCache(
        forecastJson: String,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        service: WeatherService,
    ) {
        context.weatherDataStore.edit { prefs ->
            prefs[cachedForecastJsonKey] = forecastJson
            prefs[cachedForecastTimestampKey] = timestamp
            prefs[cachedLatitudeKey] = latitude
            prefs[cachedLongitudeKey] = longitude
            prefs[cachedForecastServiceKey] = service.name
        }
    }

    /**
     * Removes all cached forecast data. Called from the "Clear cached forecast"
     * action in Settings.
     */
    suspend fun clearCache() {
        context.weatherDataStore.edit { prefs ->
            prefs.remove(cachedForecastJsonKey)
            prefs.remove(cachedForecastTimestampKey)
            prefs.remove(cachedLatitudeKey)
            prefs.remove(cachedLongitudeKey)
            prefs.remove(cachedForecastServiceKey)
        }
    }
}
