package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingItemWithMeta
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
}
