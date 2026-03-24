package com.closet.core.data.model

import java.time.LocalDate

/**
 * Domain model for a single day's weather forecast.
 *
 * All temperatures are stored in °C regardless of the user's display preference.
 * Convert to °F at the UI layer using [com.closet.core.data.model.TemperatureUnit].
 */
data class DailyForecast(
    val date: LocalDate,
    val tempLow: Double,          // °C
    val tempHigh: Double,         // °C
    val condition: WeatherCondition,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val uvIndex: Int?,
    val humidity: Int?,           // percent; null when not provided by the service
)
