package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Clothing brand (e.g. Nike, Zara). Name must be unique. Seeded by [com.closet.core.data.DatabaseSeeder.seedBrands]. */
@Entity(
    tableName = "brands",
    indices = [Index(value = ["normalized_name"], unique = true)]
)
data class BrandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String = name.lowercase()
)

/** Top-level clothing category (e.g. Tops, Bottoms). Seeded with a Phosphor icon name and display order. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Thermal role of this category. Values: None | Base | Mid | Outer. */
    @ColumnInfo(name = "warmth_layer", defaultValue = "None") val warmthLayer: String = "None",
)

/** Clothing subcategory (e.g. T-Shirt, Jeans) belonging to a parent [CategoryEntity]. */
@Entity(
    tableName = "subcategories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["category_id"])]
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)

/** Season lookup value (e.g. Spring, Winter). Stored with a Phosphor icon name for display. */
@Entity(tableName = "seasons")
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    /** Lower bound of this season's typical temperature range (°C). Null = no lower bound. */
    @ColumnInfo(name = "temp_low_c") val tempLowC: Double? = null,
    /** Upper bound of this season's typical temperature range (°C). Null = no upper bound. */
    @ColumnInfo(name = "temp_high_c") val tempHighC: Double? = null,
)

/** Occasion lookup value (e.g. Casual, Formal). Stored with a Phosphor icon name for display. */
@Entity(tableName = "occasions")
data class OccasionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null
)

/** Color lookup value (e.g. Navy, Beige) with an optional hex code for color-matching. */
@Entity(tableName = "colors")
data class ColorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hex: String? = null
)

/** Fabric/material lookup value (e.g. Cotton, Wool). */
@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** Visual pattern lookup value (e.g. Solid, Striped, Floral). Stored with a Phosphor icon name for display. */
@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null
)

/** A named sizing system (e.g. Letter, Women's Numeric, Shoes (US Men's)). */
@Entity(tableName = "size_systems")
data class SizeSystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** A single size value (e.g. "M", "8", "10.5") within a [SizeSystemEntity]. */
@Entity(
    tableName = "size_values",
    foreignKeys = [
        ForeignKey(
            entity = SizeSystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["size_system_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["size_system_id"])]
)
data class SizeValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "size_system_id") val sizeSystemId: Long,
    val value: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)
