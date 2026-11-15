package com.closet.core.data

import androidx.room.TypeConverter
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.WashStatus
import com.closet.core.data.model.WeatherCondition
import java.time.Instant

/**
 * Room [TypeConverter]s for domain types that cannot be stored natively in SQLite.
 *
 * Registered via `@TypeConverters(Converters::class)` on [com.closet.core.data.ClothingDatabase].
 * Converts [Instant] ↔ ISO-8601 string and the three status enums ↔ their string labels.
 */
class Converters {

    /** Parses an ISO-8601 timestamp string to [Instant], or returns null for a null input. */
    @TypeConverter
    fun fromTimestamp(value: String?): Instant? {
        return value?.let { Instant.parse(it) }
    }

    /** Serializes an [Instant] to its ISO-8601 string representation for storage. */
    @TypeConverter
    fun dateToTimestamp(date: Instant?): String? {
        return date?.toString()
    }

    /** Serializes [ClothingStatus] to its string label for storage. */
    @TypeConverter
    fun fromClothingStatus(status: ClothingStatus): String {
        return status.label
    }

    /** Deserializes a stored label string back to a [ClothingStatus] value. */
    @TypeConverter
    fun toClothingStatus(value: String): ClothingStatus {
        return ClothingStatus.fromString(value)
    }

    /** Serializes [WashStatus] to its string label for storage. */
    @TypeConverter
    fun fromWashStatus(status: WashStatus): String {
        return status.label
    }

    /** Deserializes a stored label string back to a [WashStatus] value. */
    @TypeConverter
    fun toWashStatus(value: String): WashStatus {
        return WashStatus.fromString(value)
    }

    /** Serializes a nullable [WeatherCondition] to its string label, or null. */
    @TypeConverter
    fun fromWeatherCondition(condition: WeatherCondition?): String? {
        return condition?.label
    }

    /** Deserializes a nullable label string back to a [WeatherCondition] value. */
    @TypeConverter
    fun toWeatherCondition(value: String?): WeatherCondition? {
        return WeatherCondition.fromString(value)
    }
}
