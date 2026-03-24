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

private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

/**
 * Open-Meteo weather client. Free, no API key required.
 *
 * Fetches a 7-day daily forecast. [humidity] is not available in the daily
 * endpoint (Open-Meteo exposes it hourly only) and is always `null`.
 *
 * Reference: https://open-meteo.com/en/docs
 */
@Singleton
class OpenMeteoClient @Inject constructor(
    private val client: HttpClient,
) : WeatherServiceClient {

    override suspend fun fetchDailyForecast(lat: Double, lon: Double): Result<List<DailyForecast>> =
        runCatching {
            val response: OpenMeteoResponse = client.get(BASE_URL) {
                parameter("latitude", lat)
                parameter("longitude", lon)
                parameter(
                    "daily",
                    "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                        "windspeed_10m_max,weathercode,uv_index_max",
                )
                parameter("forecast_days", 7)
                parameter("timezone", "auto")
            }.body()
            response.toForecasts()
        }.onFailure { Timber.e(it, "OpenMeteoClient: fetch failed") }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Serializable
    private data class OpenMeteoResponse(val daily: DailyData) {
        fun toForecasts(): List<DailyForecast> = daily.time.indices.map { i ->
            DailyForecast(
                date = LocalDate.parse(daily.time[i]),
                tempLow = daily.temperatureMin[i],
                tempHigh = daily.temperatureMax[i],
                condition = WeatherCondition.fromWmoCode(daily.weathercode[i]),
                precipitationMm = daily.precipitationSum[i],
                windSpeedKmh = daily.windspeedMax[i],
                uvIndex = daily.uvIndexMax.getOrNull(i)?.toInt(),
                humidity = null,
            )
        }
    }

    @Serializable
    private data class DailyData(
        val time: List<String>,
        @SerialName("temperature_2m_max") val temperatureMax: List<Double>,
        @SerialName("temperature_2m_min") val temperatureMin: List<Double>,
        @SerialName("precipitation_sum") val precipitationSum: List<Double>,
        @SerialName("windspeed_10m_max") val windspeedMax: List<Double>,
        val weathercode: List<Int>,
        @SerialName("uv_index_max") val uvIndexMax: List<Double> = emptyList(),
    )
}
