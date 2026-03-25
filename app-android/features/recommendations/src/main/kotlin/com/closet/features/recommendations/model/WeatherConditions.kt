package com.closet.features.recommendations.model

/**
 * Weather conditions for today, passed directly to the recommendation engine.
 *
 * The engine receives this as a plain parameter — it never calls WeatherRepository
 * directly. The UI layer is responsible for populating this from the WeatherRepository
 * cache (or from user-entered values on the weather sheet) before invoking the engine.
 *
 * @property tempLowC Today's forecast low temperature in Celsius. Null if unknown.
 * @property tempHighC Today's forecast high temperature in Celsius. Null if unknown.
 * @property isRaining True if precipitation is expected today (precipitation_mm > 1.0 threshold).
 * @property isWindy True if wind is expected today (wind_speed_kmh > 30 threshold).
 */
data class WeatherConditions(
    val tempLowC: Double?,
    val tempHighC: Double?,
    val isRaining: Boolean,
    val isWindy: Boolean
)
