package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Correlated subquery that counts distinct outfit log entries for a clothing item.
 * Requires the `ci` alias to be in scope (clothing_items). Alias `wear_count` is included.
 */
private const val WEAR_COUNT_SUBQUERY = """(
        SELECT COUNT(DISTINCT ol.id)
        FROM outfit_logs ol
        JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
        WHERE oi.clothing_item_id = ci.id
    ) AS wear_count"""

/**
 * Data Access Object for clothing items and their associated metadata.
 * Provides methods for querying, inserting, updating, and deleting items.
 * Parity: This is the native equivalent of queries.ts from the Expo project.
 */
@Dao
interface ClothingDao {

    /**
     * Retrieves all clothing items with their category, subcategory, and wear count.
     * Items are ordered by creation date in descending order (newest first).
     * @return A [Flow] emitting a list of [ClothingItemWithMeta].
     */
    @Query("""
        SELECT
            ci.id, ci.name, b.name AS brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            """ + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
        LEFT JOIN brands        b  ON ci.brand_id       = b.id
        ORDER BY ci.created_at DESC
    """)
    fun getAllClothingItems(): Flow<List<ClothingItemWithMeta>>

    /**
     * Retrieves a single clothing item with meta by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return The [ClothingItemWithMeta] if found, null otherwise.
     */
    @Query("""
        SELECT
            ci.id, ci.name, b.name AS brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            """ + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
        LEFT JOIN brands        b  ON ci.brand_id       = b.id
        WHERE ci.id = :id
    """)
    suspend fun getClothingItemById(id: Long): ClothingItemWithMeta?

    /**
     * Retrieves all clothing items with full associations for filtering and listing.
     * @return A [Flow] emitting a list of [ClothingItemDetail].
     */
    @Transaction
    @Query("SELECT ci.*, " + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        ORDER BY ci.created_at DESC
    """)
    fun getAllClothingItemDetails(): Flow<List<ClothingItemDetail>>

    /**
     * Retrieves the detailed clothing item with all associations by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return A [Flow] emitting the [ClothingItemDetail] if found.
     */
    @Transaction
    @Query("SELECT ci.*, " + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        WHERE ci.id = :id
    """)
    fun getClothingItemDetail(id: Long): Flow<ClothingItemDetail?>

    /**
     * Retrieves the raw clothing item entity by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return The [ClothingItemEntity] if found, null otherwise.
     */
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getClothingItemEntityById(id: Long): ClothingItemEntity?

    /**
     * Retrieves the colors associated with a clothing item.
     */
    @Query("""
        SELECT c.* FROM colors c
        JOIN clothing_item_colors cic ON c.id = cic.color_id
        WHERE cic.clothing_item_id = :itemId
    """)
    fun getItemColors(itemId: Long): Flow<List<ColorEntity>>

    /**
     * Inserts a new clothing item into the database.
     * @param item The [ClothingItemEntity] to insert.
     * @return The row ID of the newly inserted item.
     */
    @Insert
    suspend fun insertClothingItem(item: ClothingItemEntity): Long

    /**
     * Updates an existing clothing item in the database.
     * @param item The [ClothingItemEntity] with updated values.
     * @return The number of rows affected.
     */
    @Update
    suspend fun updateClothingItem(item: ClothingItemEntity): Int

    /**
     * Deletes a clothing item from the database by its ID.
     */
    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingItem(id: Long)
    
    /**
     * Updates the wash status and modification timestamp for a specific clothing item.
     */
    @Query("UPDATE clothing_items SET wash_status = :washStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateWashStatus(id: Long, washStatus: String, updatedAt: String)

    /**
     * Updates the favorite status and modification timestamp for a specific clothing item.
     */
    @Query("UPDATE clothing_items SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Int, updatedAt: String)

    // --- Junction Table Helpers (Atomically replace associations) ---

    /**
     * Updates the color associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) {
        deleteItemColors(itemId)
        insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_colors WHERE clothing_item_id = :itemId")
    suspend fun deleteItemColors(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemColors(items: List<ClothingItemColorEntity>)

    /**
     * Updates the material associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) {
        deleteItemMaterials(itemId)
        insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_materials WHERE clothing_item_id = :itemId")
    suspend fun deleteItemMaterials(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemMaterials(items: List<ClothingItemMaterialEntity>)

    /**
     * Updates the season associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>) {
        deleteItemSeasons(itemId)
        insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_seasons WHERE clothing_item_id = :itemId")
    suspend fun deleteItemSeasons(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemSeasons(items: List<ClothingItemSeasonEntity>)

    /**
     * Updates the occasion associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>) {
        deleteItemOccasions(itemId)
        insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_occasions WHERE clothing_item_id = :itemId")
    suspend fun deleteItemOccasions(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemOccasions(items: List<ClothingItemOccasionEntity>)

    /**
     * Updates the pattern associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>) {
        deleteItemPatterns(itemId)
        insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_patterns WHERE clothing_item_id = :itemId")
    suspend fun deleteItemPatterns(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemPatterns(items: List<ClothingItemPatternEntity>)

    // ── Batch segmentation ───────────────────────────────────────────────────

    /** Returns all items that have an image and have not yet been segmented (no .png extension). */
    @Query("SELECT * FROM clothing_items WHERE image_path IS NOT NULL AND image_path NOT LIKE '%.png'")
    suspend fun getItemsNeedingSegmentation(): List<ClothingItemEntity>

    /** Live count of items eligible for batch segmentation. Updates whenever the table changes. */
    @Query("SELECT COUNT(*) FROM clothing_items WHERE image_path IS NOT NULL AND image_path NOT LIKE '%.png'")
    fun getSegmentationEligibleCount(): Flow<Int>

    /** Replaces the stored image path for a single item and updates its timestamp. */
    @Query("UPDATE clothing_items SET image_path = :imagePath, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateItemImagePath(id: Long, imagePath: String, updatedAt: java.time.Instant)
}
