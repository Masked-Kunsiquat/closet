package com.closet.features.outfits

import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_ac_unit
import com.closet.core.ui.resources.ic_symbol_air
import com.closet.core.ui.resources.ic_symbol_cloud
import com.closet.core.ui.resources.ic_symbol_grain
import com.closet.core.ui.resources.ic_symbol_partly_cloudy_day
import com.closet.core.ui.resources.ic_symbol_sunny
import com.closet.core.ui.resources.ic_symbol_thunderstorm
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource

internal fun WeatherCondition.icon(): DrawableResource = when (this) {
    WeatherCondition.Sunny        -> CoreRes.drawable.ic_symbol_sunny
    WeatherCondition.PartlyCloudy -> CoreRes.drawable.ic_symbol_partly_cloudy_day
    WeatherCondition.Cloudy       -> CoreRes.drawable.ic_symbol_cloud
    WeatherCondition.Rainy        -> CoreRes.drawable.ic_symbol_grain
    WeatherCondition.Snowy        -> CoreRes.drawable.ic_symbol_ac_unit
    WeatherCondition.Windy        -> CoreRes.drawable.ic_symbol_air
    WeatherCondition.Thunderstorm -> CoreRes.drawable.ic_symbol_thunderstorm
    WeatherCondition.Foggy        -> CoreRes.drawable.ic_symbol_cloud
    WeatherCondition.Drizzle      -> CoreRes.drawable.ic_symbol_grain
    WeatherCondition.Sleet        -> CoreRes.drawable.ic_symbol_ac_unit
    WeatherCondition.HeavySnow    -> CoreRes.drawable.ic_symbol_ac_unit
}

/** Converts a °C value to the user's preferred display unit and formats it as "N°". */
internal fun Double.toDisplayTemp(unit: TemperatureUnit): String {
    val value = if (unit == TemperatureUnit.Fahrenheit) (this * 9.0 / 5.0 + 32) else this
    return "${value.roundToInt()}°"
}
