package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Mirror of getAllClothingItems, getClothingItemById from queries.ts.
 */
@Dao
interface ClothingDao {

    @Query("""
        SELECT
            ci.*,
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

    @Query("""
        SELECT
            ci.*,
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

    @Insert
    suspend fun insertClothingItem(item: ClothingItemEntity): Long

    @Update
    suspend fun updateClothingItem(item: ClothingItemEntity)

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingItem(id: Long)
    
    @Query("UPDATE clothing_items SET wash_status = :washStatus, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateWashStatus(id: Long, washStatus: String)

    // --- Junction Table Helpers (Atomically replace associations) ---

    @Transaction
    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) {
        deleteItemColors(itemId)
        insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_colors WHERE clothing_item_id = :itemId")
    suspend fun deleteItemColors(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemColors(items: List<ClothingItemColorEntity>)

    @Transaction
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) {
        deleteItemMaterials(itemId)
        insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_materials WHERE clothing_item_id = :itemId")
    suspend fun deleteItemMaterials(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemMaterials(items: List<ClothingItemMaterialEntity>)

    @Transaction
    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>) {
        deleteItemSeasons(itemId)
        insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_seasons WHERE clothing_item_id = :itemId")
    suspend fun deleteItemSeasons(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemSeasons(items: List<ClothingItemSeasonEntity>)

    @Transaction
    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>) {
        deleteItemOccasions(itemId)
        insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_occasions WHERE clothing_item_id = :itemId")
    suspend fun deleteItemOccasions(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemOccasions(items: List<ClothingItemOccasionEntity>)

    @Transaction
    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>) {
        deleteItemPatterns(itemId)
        insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_patterns WHERE clothing_item_id = :itemId")
    suspend fun deleteItemPatterns(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemPatterns(items: List<ClothingItemPatternEntity>)
}
