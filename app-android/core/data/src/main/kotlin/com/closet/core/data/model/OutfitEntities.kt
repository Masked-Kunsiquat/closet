package com.closet.core.data.model

import androidx.room.*
import java.time.Instant

@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now()
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
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "pos_x") val posX: Float? = null,
    @ColumnInfo(name = "pos_y") val posY: Float? = null,
    val scale: Float? = null,
    @ColumnInfo(name = "z_index") val zIndex: Int? = null
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
        Index(value = ["date"], unique = false, orders = [Index.Order.ASC])
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
    @ColumnInfo(name = "weather_condition") val weatherCondition: WeatherCondition? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now()
)

/**
 * Mirror of OutfitWithItems from types.ts.
 * Leverage: Fetches the Outfit along with its items and their layout metadata.
 */
data class OutfitWithItems(
    @Embedded val outfit: OutfitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "outfit_id"
    )
    val items: List<OutfitItemWithClothing>
)

/**
 * Intermediate data class to pair an OutfitItemEntity with its ClothingItemEntity.
 */
data class OutfitItemWithClothing(
    @Embedded val outfitItem: OutfitItemEntity,
    @Relation(
        parentColumn = "clothing_item_id",
        entityColumn = "id"
    )
    val clothingItem: ClothingItemEntity
)
