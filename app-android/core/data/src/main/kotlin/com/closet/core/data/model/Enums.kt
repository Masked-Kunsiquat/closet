package com.closet.core.data.model

/**
 * Parity: Clothing item status options.
 */
enum class ClothingStatus(val label: String) {
    Active("Active"),
    Sold("Sold"),
    Donated("Donated"),
    Lost("Lost");

    companion object {
        fun fromString(value: String): ClothingStatus {
            return entries.find { it.label == value } ?: Active
        }
    }
}

/**
 * Parity: Wash status options.
 */
enum class WashStatus(val label: String) {
    Clean("Clean"),
    Dirty("Dirty");

    companion object {
        fun fromString(value: String): WashStatus {
            return entries.find { it.label == value } ?: Clean
        }
    }
}

/**
 * Weather service provider options.
 */
enum class WeatherService(val label: String) {
    OpenMeteo("Open-Meteo"),
    Nws("NWS"),
    Google("Google");

    companion object {
        fun fromString(value: String): WeatherService {
            return entries.find { it.name == value } ?: OpenMeteo
        }
    }
}

/**
 * Temperature display unit. Canonical storage is always °C; convert for display only.
 */
enum class TemperatureUnit(val label: String) {
    Celsius("°C"),
    Fahrenheit("°F");

    companion object {
        fun fromString(value: String): TemperatureUnit {
            return entries.find { it.name == value } ?: Celsius
        }
    }
}

/**
 * Parity: Weather condition options.
 */
enum class WeatherCondition(val label: String) {
    Sunny("Sunny"),
    PartlyCloudy("Partly Cloudy"),
    Cloudy("Cloudy"),
    Rainy("Rainy"),
    Snowy("Snowy"),
    Windy("Windy");

    companion object {
        fun fromString(value: String?): WeatherCondition? {
            return entries.find { it.label == value }
        }
    }
}
