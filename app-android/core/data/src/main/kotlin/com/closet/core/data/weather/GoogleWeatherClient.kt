package com.closet.core.data.weather

import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.WeatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE_URL = "https://weather.googleapis.com/v1/forecast/days:lookup"

/**
 * Google Weather API client. Requires a valid API key.
 *
 * Does **not** implement [WeatherServiceClient] because it requires an API key
 * parameter that the interface contract doesn't carry. [WeatherRepository] calls
 * this client directly after reading and validating the key from
 * [com.closet.core.data.repository.WeatherPreferencesRepository].
 *
 * IMPORTANT: The response DTOs below are based on the Google Weather API design
 * as of early 2025. Verify field names and endpoint against current Google
 * Weather API documentation if responses are empty or parsing fails. The
 * `ignoreUnknownKeys = true` JSON config means extra fields won't cause crashes,
 * but renamed/removed fields will silently default to null/0.
 *
 * Reference: https://developers.google.com/maps/documentation/weather
 */
@Singleton
class GoogleWeatherClient @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun fetchDailyForecast(
        lat: Double,
        lon: Double,
        apiKey: String,
    ): Result<List<DailyForecast>> =
        runCatching {
            val response: GoogleForecastResponse = client.get(BASE_URL) {
                parameter("key", apiKey)
                parameter("location.latitude", lat)
                parameter("location.longitude", lon)
                parameter("days", 7)
                parameter("unitsSystem", "METRIC")
            }.body()
            response.toForecasts()
        }.onFailure { Timber.e(it, "GoogleWeatherClient: fetch failed") }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Serializable
    private data class GoogleForecastResponse(
        @SerialName("forecastDays") val forecastDays: List<GoogleForecastDay> = emptyList(),
    ) {
        fun toForecasts(): List<DailyForecast> = forecastDays.mapIndexed { i, day ->
            val startTime = day.interval?.startTime
            val date = if (startTime != null) {
                runCatching { LocalDate.parse(startTime.take(10)) }.getOrNull()
            } else null
            DailyForecast(
                date = date ?: LocalDate.now().plusDays(i.toLong()),
                tempLow = day.minTemperature?.degrees ?: 0.0,
                tempHigh = day.maxTemperature?.degrees ?: 0.0,
                condition = mapConditionType(day.daytimeForecast?.weatherCondition?.type),
                precipitationMm = day.daytimeForecast?.precipitation?.qpf?.quantity ?: 0.0,
                windSpeedKmh = day.maxWindSpeed?.value ?: 0.0,
                uvIndex = day.uvIndex,
                humidity = null,
            )
        }
    }

    @Serializable
    private data class GoogleForecastDay(
        val interval: TimeInterval? = null,
        @SerialName("maxTemperature") val maxTemperature: Temperature? = null,
        @SerialName("minTemperature") val minTemperature: Temperature? = null,
        @SerialName("daytimeForecast") val daytimeForecast: DaytimeForecast? = null,
        @SerialName("maxWindSpeed") val maxWindSpeed: WindSpeed? = null,
        val uvIndex: Int? = null,
    )

    @Serializable
    private data class TimeInterval(val startTime: String? = null)

    @Serializable
    private data class Temperature(val degrees: Double = 0.0)

    @Serializable
    private data class WindSpeed(val value: Double = 0.0)

    @Serializable
    private data class DaytimeForecast(
        val weatherCondition: WeatherConditionDto? = null,
        val precipitation: Precipitation? = null,
    )

    @Serializable
    private data class WeatherConditionDto(val type: String? = null)

    @Serializable
    private data class Precipitation(val qpf: Qpf? = null)

    @Serializable
    private data class Qpf(val quantity: Double = 0.0)

    private fun mapConditionType(type: String?): WeatherCondition = when (type?.uppercase()) {
        "CLEAR" -> WeatherCondition.Sunny
        "PARTLY_CLOUDY" -> WeatherCondition.PartlyCloudy
        "MOSTLY_CLOUDY", "CLOUDY" -> WeatherCondition.Cloudy
        "FOGGY", "FOG" -> WeatherCondition.Foggy
        "DRIZZLE", "LIGHT_RAIN" -> WeatherCondition.Drizzle
        "RAIN", "HEAVY_RAIN", "RAIN_SHOWERS" -> WeatherCondition.Rainy
        "FREEZING_RAIN", "SLEET" -> WeatherCondition.Sleet
        "SNOW", "LIGHT_SNOW", "SNOW_SHOWERS" -> WeatherCondition.Snowy
        "HEAVY_SNOW", "BLIZZARD" -> WeatherCondition.HeavySnow
        "THUNDERSTORM" -> WeatherCondition.Thunderstorm
        "WINDY" -> WeatherCondition.Windy
        else -> WeatherCondition.Cloudy
    }
}
