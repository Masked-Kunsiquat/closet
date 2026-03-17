package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = Instant.now().toString(),
    @ColumnInfo(name = "updated_at") val updatedAt: String = Instant.now().toString()
)

@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfit_id", "clothing_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfit_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["clothing_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clothing_item_id"])]
)
data class OutfitItemEntity(
    @ColumnInfo(name = "outfit_id") val outfitId: Long,
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long
)

@Entity(
    tableName = "outfit_logs",
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfit_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["outfit_id"]),
        // Parity: CREATE UNIQUE INDEX one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1
        Index(value = ["date"], unique = true, orders = [Index.Order.ASC]) // Partial index handled in manual SQL if possible, or validated in Room.
    ]
)
data class OutfitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "outfit_id") val outfitId: Long? = null,
    val date: String, // YYYY-MM-DD
    @ColumnInfo(name = "is_ootd") val isOotd: Int = 0, // 0 | 1
    val notes: String? = null,
    @ColumnInfo(name = "temperature_low") val temperatureLow: Double? = null,
    @ColumnInfo(name = "temperature_high") val temperatureHigh: Double? = null,
    @ColumnInfo(name = "weather_condition") val weatherCondition: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = Instant.now().toString()
)
