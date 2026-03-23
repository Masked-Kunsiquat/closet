package com.closet.core.data.model

import androidx.room.*
import java.time.Instant

/**
 * Represents a named collection of clothing items that the user has grouped together as an outfit.
 * An outfit acts as a template — it can be logged multiple times on different dates via [OutfitLogEntity].
 */
@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now()
)

/**
 * Junction entity linking a clothing item to an outfit, with optional layout metadata
 * (position and scale) for use in the collage-style outfit builder.
 * Uses a composite primary key of [outfitId] + [clothingItemId].
 */
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

/**
 * Records a single instance of an outfit being worn on a given [date] (YYYY-MM-DD).
 * A unique index on ([outfitId], [date]) prevents logging the same outfit twice on the same day.
 * The OOTD partial index (enforced via [onOpen] in ClothingDatabase) allows at most one
 * log per day to be marked as the outfit-of-the-day ([isOotd] = 1).
 * [outfitId] is nullable so historical log entries survive outfit deletion (SET NULL).
 */
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
        Index(value = ["date"], unique = false, orders = [Index.Order.ASC]),
        Index(value = ["outfit_id", "date"], unique = true) // prevents duplicate same-day logs per outfit
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
 * Snapshot of which clothing items were part of an outfit at the moment a log was written.
 * Prevents retroactive outfit edits from altering historical wear records.
 *
 * Rows are inserted alongside [OutfitLogEntity] in [LogDao.insertLogAndSnapshot].
 * Cascade-deletes when the parent log is deleted.
 */
@Entity(
    tableName = "outfit_log_items",
    primaryKeys = ["outfit_log_id", "clothing_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfit_log_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clothing_item_id"])]
)
data class OutfitLogItemEntity(
    @ColumnInfo(name = "outfit_log_id") val outfitLogId: Long,
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    /** Snapshot of the outfit name at log time — survives renames and outfit deletion. */
    @ColumnInfo(name = "outfit_name") val outfitName: String? = null
)

/**
 * Mirror of OutfitWithItems from types.ts.
 * Leverage: Fetches the Outfit along with its items and their layout metadata.
 */
data class OutfitWithItems(
    @Embedded val outfit: OutfitEntity,
    @Relation(
        entity = OutfitItemEntity::class,
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
        entity = ClothingItemEntity::class,
        parentColumn = "clothing_item_id",
        entityColumn = "id"
    )
    val clothingItem: ClothingItemEntity
)

/**
 * Representation of an outfit with aggregated metadata for display.
 * Parity: This is the native equivalent of OutfitWithMeta from types.ts.
 */
data class OutfitWithMeta(
    val id: Long,
    val name: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "cover_image") val coverImage: String?
)
