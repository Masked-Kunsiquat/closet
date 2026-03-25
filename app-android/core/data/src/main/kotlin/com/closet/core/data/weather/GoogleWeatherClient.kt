package com.closet.core.data.weather

import com.closet.core.data.model.DailyForecast
import com.closet.core.data.model.WeatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
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
 * Response DTOs reflect the Google Weather API v1 daily forecast schema.
 * Temperatures are returned in the unit specified by the API (may be Fahrenheit
 * depending on locale); they are always converted to °C before returning.
 * Wind speed is always converted to km/h.
 *
 * Reference: https://developers.google.com/maps/documentation/weather/daily-forecast
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
                parameter("pageSize", 7)
            }.body()
            response.toForecasts()
        }.onFailure { if (it is CancellationException) throw it else Timber.e(it, "GoogleWeatherClient: fetch failed") }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Serializable
    private data class GoogleForecastResponse(
        @SerialName("forecastDays") val forecastDays: List<GoogleForecastDay> = emptyList(),
    ) {
        fun toForecasts(): List<DailyForecast> = forecastDays.mapIndexed { i, day ->
            val date = day.displayDate?.toLocalDate()
                ?: LocalDate.now().plusDays(i.toLong())
            DailyForecast(
                date = date,
                tempLow = day.minTemperature?.toCelsius() ?: 0.0,
                tempHigh = day.maxTemperature?.toCelsius() ?: 0.0,
                condition = mapConditionType(day.daytimeForecast?.weatherCondition?.type),
                precipitationMm = day.daytimeForecast?.precipitation?.qpf?.quantity ?: 0.0,
                windSpeedKmh = day.daytimeForecast?.wind?.speed?.toKmh() ?: 0.0,
                uvIndex = day.daytimeForecast?.uvIndex,
                humidity = day.daytimeForecast?.relativeHumidity,
            )
        }
    }

    @Serializable
    private data class GoogleForecastDay(
        val displayDate: DisplayDate? = null,
        @SerialName("maxTemperature") val maxTemperature: Temperature? = null,
        @SerialName("minTemperature") val minTemperature: Temperature? = null,
        @SerialName("daytimeForecast") val daytimeForecast: DaytimeForecast? = null,
    )

    @Serializable
    private data class DisplayDate(
        val year: Int = 0,
        val month: Int = 0,
        val day: Int = 0,
    ) {
        fun toLocalDate(): LocalDate? = runCatching {
            LocalDate.of(year, month, day)
        }.getOrNull()
    }

    @Serializable
    private data class Temperature(
        val degrees: Double = 0.0,
        val unit: String = "CELSIUS",
    ) {
        fun toCelsius(): Double = if (unit == "FAHRENHEIT") (degrees - 32) * 5.0 / 9.0 else degrees
    }

    @Serializable
    private data class DaytimeForecast(
        val weatherCondition: WeatherConditionDto? = null,
        val precipitation: Precipitation? = null,
        val wind: Wind? = null,
        val uvIndex: Int? = null,
        val relativeHumidity: Int? = null,
    )

    @Serializable
    private data class WeatherConditionDto(val type: String? = null)

    @Serializable
    private data class Precipitation(val qpf: Qpf? = null)

    @Serializable
    private data class Qpf(val quantity: Double = 0.0)

    @Serializable
    private data class Wind(val speed: WindSpeed? = null)

    @Serializable
    private data class WindSpeed(
        val value: Double = 0.0,
        val unit: String = "KILOMETERS_PER_HOUR",
    ) {
        fun toKmh(): Double = if (unit == "MILES_PER_HOUR") value * 1.60934 else value
    }

    companion object {
        private fun mapConditionType(type: String?): WeatherCondition = when (type?.uppercase()) {
            "CLEAR", "MOSTLY_CLEAR" -> WeatherCondition.Sunny
            "PARTLY_CLOUDY" -> WeatherCondition.PartlyCloudy
            "MOSTLY_CLOUDY", "CLOUDY" -> WeatherCondition.Cloudy
            "FOGGY", "FOG" -> WeatherCondition.Foggy
            "DRIZZLE", "LIGHT_RAIN", "LIGHT_RAIN_SHOWERS" -> WeatherCondition.Drizzle
            "RAIN", "HEAVY_RAIN", "RAIN_SHOWERS", "WIND_AND_RAIN", "SCATTERED_SHOWERS",
            "HEAVY_RAIN_SHOWERS" -> WeatherCondition.Rainy
            "FREEZING_RAIN", "SLEET", "RAIN_AND_SNOW" -> WeatherCondition.Sleet
            "SNOW", "LIGHT_SNOW", "SNOW_SHOWERS" -> WeatherCondition.Snowy
            "HEAVY_SNOW", "BLIZZARD" -> WeatherCondition.HeavySnow
            "THUNDERSTORM", "THUNDERSHOWER" -> WeatherCondition.Thunderstorm
            "WINDY", "SQUALL" -> WeatherCondition.Windy
            else -> WeatherCondition.Cloudy
        }
    }
}
