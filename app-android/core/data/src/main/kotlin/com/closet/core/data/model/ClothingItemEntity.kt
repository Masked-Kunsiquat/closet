package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Mirror of ClothingItem type from the Expo project.
 * Upgraded with Enums and Instant for Native Best Practices.
 */
@Entity(
    tableName = "clothing_items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategory_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SizeValueEntity::class,
            parentColumns = ["id"],
            childColumns = ["size_value_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["subcategory_id"]),
        Index(value = ["size_value_id"])
    ]
)
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val brand: String? = null,
    
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,
    
    @ColumnInfo(name = "subcategory_id")
    val subcategoryId: Long? = null,
    
    @ColumnInfo(name = "size_value_id")
    val sizeValueId: Long? = null,
    
    val waist: Double? = null,
    val inseam: Double? = null,
    
    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Double? = null,
    
    @ColumnInfo(name = "purchase_date")
    val purchaseDate: String? = null, // YYYY-MM-DD
    
    @ColumnInfo(name = "purchase_location")
    val purchaseLocation: String? = null,
    
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    
    val notes: String? = null,
    
    @ColumnInfo(defaultValue = "Active")
    val status: ClothingStatus = ClothingStatus.Active,
    
    @ColumnInfo(name = "wash_status", defaultValue = "Clean")
    val washStatus: WashStatus = WashStatus.Clean,
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Int = 0, // 0 | 1
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)

/**
 * Data class for join results, mirroring ClothingItemWithMeta.
 * Parity check: includes category_name, subcategory_name, and wear_count.
 * Leverage: Includes computed cost_per_wear.
 */
data class ClothingItemWithMeta(
    val id: Long,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "category_name")
    val categoryName: String?,
    @ColumnInfo(name = "subcategory_name")
    val subcategoryName: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "wear_count")
    val wearCount: Int,
    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Double?,
    val status: ClothingStatus,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Int
) {
    /** 
     * Computed at runtime for parity with "built well" rules.
     * purchase_price / wear_count
     */
    @Suppress("unused")
    val costPerWear: Double?
        get() = if (wearCount > 0) (purchasePrice ?: 0.0) / wearCount else purchasePrice
}
