package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Data Access Object for managing outfits and their constituent items.
 * Parity: This is the native equivalent of Outfit queries from queries.ts.
 */
@Dao
interface OutfitDao {

    /**
     * Retrieves all outfits with calculated metadata like item count and a cover image.
     * @return A [Flow] emitting a list of [OutfitWithMeta].
     */
    @Transaction
    @Query("""
        SELECT
            o.*,
            COUNT(oi.clothing_item_id) AS item_count,
            (SELECT ci.image_path
             FROM outfit_items oi2
             JOIN clothing_items ci ON ci.id = oi2.clothing_item_id
             WHERE oi2.outfit_id = o.id AND ci.image_path IS NOT NULL
             ORDER BY ci.id ASC
             LIMIT 1) AS cover_image
        FROM outfits o
        LEFT JOIN outfit_items oi ON oi.outfit_id = o.id
        GROUP BY o.id
        ORDER BY o.created_at DESC
    """)
    fun getAllOutfits(): Flow<List<OutfitWithMeta>>

    /**
     * Retrieves a full outfit by its unique ID.
     */
    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :id")
    fun getOutfitWithItems(id: Long): Flow<OutfitWithItems?>

    /**
     * Inserts a new outfit entry.
     * @param outfit The [OutfitEntity] to insert.
     * @return The row ID of the newly inserted outfit.
     */
    @Insert
    suspend fun insertOutfit(outfit: OutfitEntity): Long

    /**
     * Inserts multiple clothing items into an outfit.
     * @param items The list of [OutfitItemEntity] objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItems(items: List<OutfitItemEntity>)

    /**
     * Updates an existing outfit's basic information.
     * @param outfit The [OutfitEntity] with updated values.
     */
    @Update
    suspend fun updateOutfit(outfit: OutfitEntity)

    /**
     * Updates specific fields of an outfit to preserve fields like createdAt.
     * @return The number of affected rows.
     */
    @Query("UPDATE outfits SET name = :name, notes = :notes, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOutfitFields(id: Long, name: String?, notes: String?, updatedAt: Instant): Int

    /**
     * Removes all clothing item associations for a specific outfit.
     * Used typically before re-inserting updated item lists.
     * @param outfitId The ID of the outfit.
     */
    @Query("DELETE FROM outfit_items WHERE outfit_id = :outfitId")
    suspend fun deleteItemsForOutfit(outfitId: Long)

    /**
     * Deletes a specific outfit entry.
     * @param outfit The [OutfitEntity] to delete.
     */
    @Delete
    suspend fun deleteOutfit(outfit: OutfitEntity)

    /**
     * Deletes an outfit entry by its unique ID.
     * @param outfitId The ID of the outfit to delete.
     */
    @Query("DELETE FROM outfits WHERE id = :outfitId")
    suspend fun deleteOutfitById(outfitId: Long)

    /**
     * Retrieves all outfits that contain a specific clothing item.
     * @param itemId The ID of the clothing item.
     * @return A [Flow] emitting a list of [OutfitWithMeta].
     */
    @Transaction
    @Query("""
        SELECT
            o.*,
            COUNT(oi2.clothing_item_id) AS item_count,
            (SELECT ci.image_path
             FROM outfit_items oi3
             JOIN clothing_items ci ON ci.id = oi3.clothing_item_id
             WHERE oi3.outfit_id = o.id AND ci.image_path IS NOT NULL
             ORDER BY ci.id ASC
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
