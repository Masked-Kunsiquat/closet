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
    OpenMeteo("Open\u2011Meteo"),
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
 * Outfit role for a top-level clothing category.
 * Stored as TEXT in `categories.outfit_role`. Used by the recommendation engine's
 * category completeness check — never string-match on `categories.name`.
 *
 * A valid outfit is (Top + Bottom) OR (OnePiece), with optional Outerwear/Footwear/Accessory.
 */
enum class OutfitRole(val label: String) {
    Top("Top"),
    Bottom("Bottom"),
    OnePiece("OnePiece"),
    Outerwear("Outerwear"),
    Footwear("Footwear"),
    Accessory("Accessory"),
    Other("Other");

    companion object {
        fun fromString(value: String): OutfitRole =
            entries.find { it.label == value } ?: Other
    }
}

/**
 * Color family for a color lookup value.
 * Stored as TEXT in `colors.color_family`. Used by color harmony scoring in the
 * recommendation engine's outfit-level multiplier step.
 *
 * Values: Neutral | Earth | Cool | Warm | Bright.
 */
enum class ColorFamily(val label: String) {
    Neutral("Neutral"),
    Earth("Earth"),
    Cool("Cool"),
    Warm("Warm"),
    Bright("Bright");

    companion object {
        fun fromString(value: String): ColorFamily =
            entries.find { it.label == value } ?: Neutral
    }
}

/**
 * Weather condition options. Stored in the DB as [label] strings — adding new
 * entries is non-breaking for existing rows.
 *
 * Covers all WMO weather interpretation codes used by Open-Meteo; see [fromWmoCode].
 */
enum class WeatherCondition(val label: String) {
    Sunny("Sunny"),
    PartlyCloudy("Partly Cloudy"),
    Cloudy("Cloudy"),
    Rainy("Rainy"),
    Snowy("Snowy"),
    Windy("Windy"),
    Thunderstorm("Thunderstorm"),
    Foggy("Foggy"),
    Drizzle("Drizzle"),
    Sleet("Sleet"),
    HeavySnow("Heavy Snow");

    companion object {
        fun fromString(value: String?): WeatherCondition? =
            entries.find { it.label == value }

        /**
         * Maps a WMO weather interpretation code (as used by Open-Meteo) to the
         * nearest [WeatherCondition]. Returns [Cloudy] for any unrecognised code.
         *
         * Reference: https://open-meteo.com/en/docs#weathervariables
         */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> Sunny
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Foggy
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 80, 81, 82 -> Rainy
            66, 67 -> Sleet
            71, 73, 77, 85 -> Snowy
            75, 86 -> HeavySnow
            95, 96, 99 -> Thunderstorm
            else -> Cloudy
        }
    }
}
