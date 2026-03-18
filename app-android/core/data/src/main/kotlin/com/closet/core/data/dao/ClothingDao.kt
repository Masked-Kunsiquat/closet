package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

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
            ci.id, ci.name, ci.brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            (
                SELECT COUNT(DISTINCT ol.id)
                FROM outfit_logs ol
                JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
                WHERE oi.clothing_item_id = ci.id
            ) AS wear_count
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
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
            ci.id, ci.name, ci.brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            (
                SELECT COUNT(DISTINCT ol.id)
                FROM outfit_logs ol
                JOIN outfit_items oi ON ol.outfit_id = oi.outfit_id
                WHERE oi.clothing_item_id = ci.id
            ) AS wear_count
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
        WHERE ci.id = :id
    """)
    suspend fun getClothingItemById(id: Long): ClothingItemWithMeta?

    /**
     * Retrieves the raw clothing item entity by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return The [ClothingItemEntity] if found, null otherwise.
     */
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getClothingItemEntityById(id: Long): ClothingItemEntity?

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
     */
    @Update
    suspend fun updateClothingItem(item: ClothingItemEntity)

    /**
     * Deletes a clothing item from the database by its ID.
     * @param id The ID of the clothing item to delete.
     */
    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingItem(id: Long)
    
    /**
     * Updates the wash status and modification timestamp for a specific clothing item.
     * @param id The ID of the clothing item.
     * @param washStatus The new wash status label.
     */
    @Query("UPDATE clothing_items SET wash_status = :washStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateWashStatus(id: Long, washStatus: String, updatedAt: String)

    /**
     * Updates the favorite status and modification timestamp for a specific clothing item.
     * @param id The ID of the clothing item.
     * @param isFavorite 1 if favorite, 0 otherwise.
     */
    @Query("UPDATE clothing_items SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Int, updatedAt: String)

    // --- Junction Table Helpers (Atomically replace associations) ---

    /**
     * Updates the color associations for a specific clothing item.
     * Replaces all existing color associations with the provided list.
     * @param itemId The ID of the clothing item.
     * @param colorIds The list of color IDs to associate with the item.
     */
    @Transaction
    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) {
        deleteItemColors(itemId)
        insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    /**
     * Removes all color associations for a specific clothing item.
     * @param itemId The ID of the clothing item.
     */
    @Query("DELETE FROM clothing_item_colors WHERE clothing_item_id = :itemId")
    suspend fun deleteItemColors(itemId: Long)

    /**
     * Inserts a list of color associations for a clothing item.
     * @param items The list of [ClothingItemColorEntity] objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemColors(items: List<ClothingItemColorEntity>)

    /**
     * Updates the material associations for a specific clothing item.
     * @param itemId The ID of the clothing item.
     * @param materialIds The list of material IDs to associate.
     */
    @Transaction
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) {
        deleteItemMaterials(itemId)
        insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    /**
     * Removes all material associations for a specific clothing item.
     */
    @Query("DELETE FROM clothing_item_materials WHERE clothing_item_id = :itemId")
    suspend fun deleteItemMaterials(itemId: Long)

    /**
     * Inserts a list of material associations for a clothing item.
     */
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

    /**
     * Removes all season associations for a specific clothing item.
     */
    @Query("DELETE FROM clothing_item_seasons WHERE clothing_item_id = :itemId")
    suspend fun deleteItemSeasons(itemId: Long)

    /**
     * Inserts a list of season associations for a clothing item.
     */
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

    /**
     * Removes all occasion associations for a specific clothing item.
     */
    @Query("DELETE FROM clothing_item_occasions WHERE clothing_item_id = :itemId")
    suspend fun deleteItemOccasions(itemId: Long)

    /**
     * Inserts a list of occasion associations for a clothing item.
     */
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

    /**
     * Removes all pattern associations for a specific clothing item.
     */
    @Query("DELETE FROM clothing_item_patterns WHERE clothing_item_id = :itemId")
    suspend fun deleteItemPatterns(itemId: Long)

    /**
     * Inserts a list of pattern associations for a clothing item.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemPatterns(items: List<ClothingItemPatternEntity>)
}
