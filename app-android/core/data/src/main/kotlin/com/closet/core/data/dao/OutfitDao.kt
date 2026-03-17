package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Mirror of Outfit queries from queries.ts.
 */
@Dao
interface OutfitDao {

    @Transaction
    @Query("""
        SELECT
            o.*,
            COUNT(oi.clothing_item_id) AS item_count,
            (SELECT ci.image_path
             FROM outfit_items oi2
             JOIN clothing_items ci ON ci.id = oi2.clothing_item_id
             WHERE oi2.outfit_id = o.id AND ci.image_path IS NOT NULL
             LIMIT 1) AS cover_image
        FROM outfits o
        LEFT JOIN outfit_items oi ON oi.outfit_id = o.id
        GROUP BY o.id
        ORDER BY o.created_at DESC
    """)
    fun getAllOutfits(): Flow<List<OutfitWithMeta>>

    @Insert
    suspend fun insertOutfit(outfit: OutfitEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutfitItems(items: List<OutfitItemEntity>)

    @Update
    suspend fun updateOutfit(outfit: OutfitEntity)

    @Query("DELETE FROM outfit_items WHERE outfit_id = :outfitId")
    suspend fun deleteItemsForOutfit(outfitId: Long)

    @Delete
    suspend fun deleteOutfit(outfit: OutfitEntity)

    @Query("""
        SELECT
            o.*,
            COUNT(oi2.clothing_item_id) AS item_count,
            (SELECT ci.image_path
             FROM outfit_items oi3
             JOIN clothing_items ci ON ci.id = oi3.clothing_item_id
             WHERE oi3.outfit_id = o.id AND ci.image_path IS NOT NULL
             LIMIT 1) AS cover_image
        FROM outfit_items oi
        JOIN outfits o ON o.id = oi.outfit_id
        LEFT JOIN outfit_items oi2 ON oi2.outfit_id = o.id
        WHERE oi.clothing_item_id = :itemId
        GROUP BY o.id
        ORDER BY o.created_at DESC
    """)
    fun getOutfitsForItem(itemId: Long): Flow<List<OutfitWithMeta>>
}

/**
 * Helper data class for Outfit joins.
 */
data class OutfitWithMeta(
    val id: Long,
    val name: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "cover_image") val coverImage: String?
)
