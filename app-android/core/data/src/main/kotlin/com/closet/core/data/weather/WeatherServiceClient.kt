package com.closet.core.data.weather

import com.closet.core.data.model.DailyForecast

/**
 * Contract for weather service clients that return a 7-day forecast.
 *
 * Implementations:
 * - [OpenMeteoClient] — default, no API key required
 * - [NwsClient] — US-only, no API key required
 *
 * [GoogleWeatherClient] uses a separate method signature (requires an API key)
 * and does not implement this interface.
 */
interface WeatherServiceClient {
    suspend fun fetchDailyForecast(lat: Double, lon: Double): Result<List<DailyForecast>>
}
