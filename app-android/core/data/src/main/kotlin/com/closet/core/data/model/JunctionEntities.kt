package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction entity linking a clothing item to one or more [ColorEntity] values.
 * Cascade-deletes when either side is removed.
 */
@Entity(
    tableName = "clothing_item_colors",
    primaryKeys = ["clothing_item_id", "color_id"],
    foreignKeys = [
        ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["clothing_item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ColorEntity::class, parentColumns = ["id"], childColumns = ["color_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("color_id"), Index("clothing_item_id")]
)
data class ClothingItemColorEntity(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "color_id") val colorId: Long
)

/**
 * Junction entity linking a clothing item to one or more [MaterialEntity] values.
 * Cascade-deletes when either side is removed.
 */
@Entity(
    tableName = "clothing_item_materials",
    primaryKeys = ["clothing_item_id", "material_id"],
    foreignKeys = [
        ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["clothing_item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["material_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("material_id"), Index("clothing_item_id")]
)
data class ClothingItemMaterialEntity(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "material_id") val materialId: Long
)

/**
 * Junction entity linking a clothing item to one or more [SeasonEntity] values.
 * Cascade-deletes when either side is removed.
 */
@Entity(
    tableName = "clothing_item_seasons",
    primaryKeys = ["clothing_item_id", "season_id"],
    foreignKeys = [
        ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["clothing_item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SeasonEntity::class, parentColumns = ["id"], childColumns = ["season_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("season_id"), Index("clothing_item_id")]
)
data class ClothingItemSeasonEntity(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "season_id") val seasonId: Long
)

/**
 * Junction entity linking a clothing item to one or more [OccasionEntity] values.
 * Cascade-deletes when either side is removed.
 */
@Entity(
    tableName = "clothing_item_occasions",
    primaryKeys = ["clothing_item_id", "occasion_id"],
    foreignKeys = [
        ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["clothing_item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = OccasionEntity::class, parentColumns = ["id"], childColumns = ["occasion_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("occasion_id"), Index("clothing_item_id")]
)
data class ClothingItemOccasionEntity(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "occasion_id") val occasionId: Long
)

/**
 * Junction entity linking a clothing item to one or more [PatternEntity] values.
 * Cascade-deletes when either side is removed.
 */
@Entity(
    tableName = "clothing_item_patterns",
    primaryKeys = ["clothing_item_id", "pattern_id"],
    foreignKeys = [
        ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["clothing_item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PatternEntity::class, parentColumns = ["id"], childColumns = ["pattern_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("pattern_id"), Index("clothing_item_id")]
)
data class ClothingItemPatternEntity(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: Long,
    @ColumnInfo(name = "pattern_id") val patternId: Long
)
