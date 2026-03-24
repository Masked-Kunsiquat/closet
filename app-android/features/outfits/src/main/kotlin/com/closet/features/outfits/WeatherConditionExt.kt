package com.closet.features.outfits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import kotlin.math.roundToInt

internal fun WeatherCondition.icon(): ImageVector = when (this) {
    WeatherCondition.Sunny        -> Icons.Outlined.WbSunny
    WeatherCondition.PartlyCloudy -> Icons.Outlined.WbCloudy
    WeatherCondition.Cloudy       -> Icons.Outlined.Cloud
    WeatherCondition.Rainy        -> Icons.Outlined.Grain
    WeatherCondition.Snowy        -> Icons.Outlined.AcUnit
    WeatherCondition.Windy        -> Icons.Outlined.Air
    WeatherCondition.Thunderstorm -> Icons.Outlined.Thunderstorm
    WeatherCondition.Foggy        -> Icons.Outlined.Cloud
    WeatherCondition.Drizzle      -> Icons.Outlined.Grain
    WeatherCondition.Sleet        -> Icons.Outlined.AcUnit
    WeatherCondition.HeavySnow    -> Icons.Outlined.AcUnit
}

/** Converts a °C value to the user's preferred display unit and formats it as "N°". */
internal fun Double.toDisplayTemp(unit: TemperatureUnit): String {
    val value = if (unit == TemperatureUnit.Fahrenheit) (this * 9.0 / 5.0 + 32) else this
    return "${value.roundToInt()}°"
}
