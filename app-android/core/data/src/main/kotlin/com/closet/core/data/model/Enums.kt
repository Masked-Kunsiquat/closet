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
