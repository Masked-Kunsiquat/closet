package com.closet.core.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Serializable DTO used to persist a [DailyForecast] in the DataStore cache.
 *
 * Uses only primitive/String fields to avoid custom serializers for
 * [java.time.LocalDate] and [WeatherCondition]. Condition is stored by
 * [WeatherCondition.name] so it survives label renames.
 */
@Serializable
data class CachedForecastEntry(
    val dateStr: String,          // "YYYY-MM-DD"
    val tempLowC: Double,
    val tempHighC: Double,
    val conditionName: String,    // WeatherCondition.name
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val uvIndex: Int?,
    val humidity: Int?,
)

fun CachedForecastEntry.toDomain(): DailyForecast = DailyForecast(
    date = LocalDate.parse(dateStr),
    tempLow = tempLowC,
    tempHigh = tempHighC,
    condition = WeatherCondition.entries.find { it.name == conditionName }
        ?: WeatherCondition.Cloudy,
    precipitationMm = precipitationMm,
    windSpeedKmh = windSpeedKmh,
    uvIndex = uvIndex,
    humidity = humidity,
)

fun DailyForecast.toCached(): CachedForecastEntry = CachedForecastEntry(
    dateStr = date.toString(),
    tempLowC = tempLow,
    tempHighC = tempHigh,
    conditionName = condition.name,
    precipitationMm = precipitationMm,
    windSpeedKmh = windSpeedKmh,
    uvIndex = uvIndex,
    humidity = humidity,
)
