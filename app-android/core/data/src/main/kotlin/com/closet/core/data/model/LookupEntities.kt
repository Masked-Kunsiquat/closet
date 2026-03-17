package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)

@Entity(tableName = "seasons")
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null
)

@Entity(tableName = "occasions")
data class OccasionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null
)

@Entity(tableName = "colors")
data class ColorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hex: String? = null
)

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "size_systems")
data class SizeSystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "size_values",
    foreignKeys = [
        ForeignKey(
            entity = SizeSystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["size_system_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SizeValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "size_system_id") val sizeSystemId: Long,
    val value: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)
