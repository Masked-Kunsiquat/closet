package com.closet.core.data.model

import androidx.room.*
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
        ),
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["subcategory_id"]),
        Index(value = ["size_value_id"]),
        Index(value = ["brand_id"])
    ]
)
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,

    @Deprecated("Replaced by brandId FK. Do not write to this field. Retained for schema compatibility — SQLite cannot drop columns before API 35.")
    val brand: String? = null,

    @ColumnInfo(name = "brand_id")
    val brandId: Long? = null,
    
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
    val isFavorite: Int,
    @ColumnInfo(name = "wash_status")
    val washStatus: WashStatus
) {
    /** 
     * Computed at runtime for parity with "built well" rules.
     * purchase_price / wear_count
     */
    val costPerWear: Double?
        get() = if (wearCount > 0) (purchasePrice ?: 0.0) / wearCount else purchasePrice
}

/**
 * Detailed view of a clothing item including all many-to-many associations.
 */
data class ClothingItemDetail(
    @Embedded val item: ClothingItemEntity,

    @ColumnInfo(name = "wear_count")
    val wearCount: Int,

    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity?,

    @Relation(parentColumn = "subcategory_id", entityColumn = "id")
    val subcategory: SubcategoryEntity?,

    @Relation(parentColumn = "size_value_id", entityColumn = "id")
    val sizeValue: SizeValueEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemColorEntity::class,
            parentColumn = "clothing_item_id",
            entityColumn = "color_id"
        )
    )
    val colors: List<ColorEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemMaterialEntity::class,
            parentColumn = "clothing_item_id",
            entityColumn = "material_id"
        )
    )
    val materials: List<MaterialEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemSeasonEntity::class,
            parentColumn = "clothing_item_id",
            entityColumn = "season_id"
        )
    )
    val seasons: List<SeasonEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemOccasionEntity::class,
            parentColumn = "clothing_item_id",
            entityColumn = "occasion_id"
        )
    )
    val occasions: List<OccasionEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemPatternEntity::class,
            parentColumn = "clothing_item_id",
            entityColumn = "pattern_id"
        )
    )
    val patterns: List<PatternEntity>,

    @Relation(parentColumn = "brand_id", entityColumn = "id")
    val brand: BrandEntity?
) {
    val costPerWear: Double?
        get() = if (wearCount > 0) (item.purchasePrice ?: 0.0) / wearCount else item.purchasePrice
}
