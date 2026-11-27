package com.closet.features.recommendations.scoreboard

import java.time.LocalDate
import kotlin.random.Random

/** One simulated day of weather, in the units the app stores (degrees Celsius). */
data class DayWeather(val lowC: Double, val highC: Double, val raining: Boolean, val windy: Boolean) {
    /** Stored precipitation: a fixed 5.0 mm on a rainy day, else 0.0. Never compared to engine thresholds. */
    val precipitationMm: Double get() = if (raining) 5.0 else 0.0

    /** Stored wind: a fixed 40.0 km/h on a windy day, else 10.0. Never compared to engine thresholds. */
    val windSpeedKmh: Double get() = if (windy) 40.0 else 10.0
}

/** The month's mean temperatures plus noise from [Random]; always draws four numbers. */
object WeatherModel {
    private const val NOISE_SPREAD_C = 2.0

    fun day(climate: Climate, date: LocalDate, random: Random): DayWeather {
        val month = date.monthValue - 1
        val highNoise = noise(random)
        val lowNoise = noise(random)
        val rainRoll = random.nextDouble()
        val windRoll = random.nextDouble()
        val high = climate.monthlyHighC[month] + highNoise
        val low = minOf(climate.monthlyLowC[month] + lowNoise, high)
        return DayWeather(
            lowC = low,
            highC = high,
            raining = rainRoll < climate.monthlyRainChance[month],
            windy = windRoll < climate.windyChance,
        )
    }

    /** A bell-ish value in [-3, 3] times the spread, from three uniform draws. */
    private fun noise(random: Random): Double =
        (random.nextDouble() + random.nextDouble() + random.nextDouble() - 1.5) * NOISE_SPREAD_C
}
