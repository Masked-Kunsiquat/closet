package com.closet.core.data.weather

import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.WeatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val POINTS_URL = "https://api.weather.gov/points"

// NWS requires a descriptive User-Agent with contact info.
private const val USER_AGENT = "(hangr wardrobe app, github.com/Masked-Kunsiquat/closet)"

/**
 * National Weather Service client. No API key required, but US-only.
 *
 * Returns [Result.failure] with a descriptive message for non-US coordinates
 * (NWS /points returns a 404 for locations outside the US). [WeatherRepository]
 * surfaces this to the user rather than silently falling back to Open-Meteo.
 *
 * Notes:
 * - Precipitation (mm) is not available in the basic gridpoint forecast endpoint;
 *   `precipitationMm` is always 0.0.
 * - UV index and humidity are not available from this endpoint; both are `null`.
 *
 * Reference: https://www.weather.gov/documentation/services-web-api
 */
@Singleton
class NwsClient @Inject constructor(
    private val client: HttpClient,
) : WeatherServiceClient {

    override suspend fun fetchDailyForecast(lat: Double, lon: Double): Result<List<DailyForecast>> =
        runCatching {
            // Step 1 — resolve grid point for this lat/lon
            val points: NwsPointsResponse = client.get("$POINTS_URL/$lat,$lon") {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/geo+json")
            }.body()

            // Step 2 — fetch the 12-hour period forecast
            val forecast: NwsForecastResponse = client.get(points.properties.forecast) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/geo+json")
            }.body()

            mergePeriods(forecast.properties.periods)
        }.onFailure { Timber.e(it, "NwsClient: fetch failed") }

    /**
     * NWS delivers 12-hour periods (daytime + nighttime). Group by calendar date
     * and pick the daytime period's temp as [DailyForecast.tempHigh], nighttime
     * as [DailyForecast.tempLow]. Either half may be absent (e.g. first period of
     * the day is already nighttime) — fall back gracefully.
     */
    private fun mergePeriods(periods: List<NwsPeriod>): List<DailyForecast> {
        // date → (daytime period, nighttime period)
        val byDate = linkedMapOf<LocalDate, Pair<NwsPeriod?, NwsPeriod?>>()
        for (period in periods) {
            val date = OffsetDateTime.parse(period.startTime).toLocalDate()
            val existing = byDate.getOrDefault(date, Pair(null, null))
            byDate[date] = if (period.isDaytime) {
                Pair(period, existing.second)
            } else {
                Pair(existing.first, period)
            }
        }
        return byDate.entries.take(7).map { (date, pair) ->
            val day = pair.first
            val night = pair.second
            val representative = day ?: night!!
            DailyForecast(
                date = date,
                tempHigh = (day ?: night!!).temperatureCelsius,
                tempLow = (night ?: day!!).temperatureCelsius,
                condition = mapShortForecast(representative.shortForecast),
                precipitationMm = 0.0,
                windSpeedKmh = parseWindSpeedKmh(representative.windSpeed),
                uvIndex = null,
                humidity = null,
            )
        }
    }

    private fun mapShortForecast(forecast: String): WeatherCondition {
        val f = forecast.lowercase()
        return when {
            "thunder" in f -> WeatherCondition.Thunderstorm
            "heavy snow" in f || "blizzard" in f -> WeatherCondition.HeavySnow
            "snow" in f || "flurr" in f -> WeatherCondition.Snowy
            "sleet" in f || "freezing rain" in f -> WeatherCondition.Sleet
            "drizzle" in f -> WeatherCondition.Drizzle
            "rain" in f || "shower" in f -> WeatherCondition.Rainy
            "fog" in f -> WeatherCondition.Foggy
            "windy" in f || "breezy" in f -> WeatherCondition.Windy
            "sunny" in f || "clear" in f -> WeatherCondition.Sunny
            "partly" in f -> WeatherCondition.PartlyCloudy
            "mostly cloudy" in f || "overcast" in f || "cloudy" in f -> WeatherCondition.Cloudy
            else -> WeatherCondition.Cloudy
        }
    }

    /** Parses "10 mph", "10 to 15 mph", or "Calm" → km/h. Takes the higher bound. */
    private fun parseWindSpeedKmh(windSpeed: String): Double {
        if (windSpeed.contains("calm", ignoreCase = true)) return 0.0
        val mph = Regex("""\d+""").findAll(windSpeed).map { it.value.toInt() }.lastOrNull() ?: 0
        return mph * 1.60934
    }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Serializable
    private data class NwsPointsResponse(val properties: PointsProperties)

    @Serializable
    private data class PointsProperties(val forecast: String)

    @Serializable
    private data class NwsForecastResponse(val properties: ForecastProperties)

    @Serializable
    private data class ForecastProperties(val periods: List<NwsPeriod>)

    @Serializable
    private data class NwsPeriod(
        val isDaytime: Boolean,
        val temperature: Double,
        val temperatureUnit: String,  // "F" or "C"
        val windSpeed: String,
        val shortForecast: String,
        val startTime: String,
    ) {
        val temperatureCelsius: Double
            get() = if (temperatureUnit == "F") (temperature - 32) * 5.0 / 9.0 else temperature
    }
}
