package com.closet.core.data

import androidx.room.TypeConverter
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.WashStatus
import com.closet.core.data.model.WeatherCondition
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): Instant? {
        return value?.let { Instant.parse(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun fromClothingStatus(status: ClothingStatus): String {
        return status.label
    }

    @TypeConverter
    fun toClothingStatus(value: String): ClothingStatus {
        return ClothingStatus.fromString(value)
    }

    @TypeConverter
    fun fromWashStatus(status: WashStatus): String {
        return status.label
    }

    @TypeConverter
    fun toWashStatus(value: String): WashStatus {
        return WashStatus.fromString(value)
    }

    @TypeConverter
    fun fromWeatherCondition(condition: WeatherCondition?): String? {
        return condition?.label
    }

    @TypeConverter
    fun toWeatherCondition(value: String?): WeatherCondition? {
        return WeatherCondition.fromString(value)
    }
}
