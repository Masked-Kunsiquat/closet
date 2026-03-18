package com.closet.core.data.repository

import androidx.room.withTransaction
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.OutfitDao
import com.closet.core.data.dao.OutfitWithMeta
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.model.OutfitItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Outfit operations.
 * Manages the grouping of clothing items into cohesive outfits.
 * Parity: This is the native equivalent of Outfit queries in hangr/db/queries.ts.
 */
@Singleton
class OutfitRepository @Inject constructor(
    private val database: ClothingDatabase,
    private val outfitDao: OutfitDao
) {
    /**
     * Retrieves all outfits with their calculated metadata (item count and cover image).
     * @return A [Flow] emitting a list of [OutfitWithMeta].
     */
    fun getAllOutfits(): Flow<List<OutfitWithMeta>> = outfitDao.getAllOutfits()

    /**
     * Creates a new outfit and associates it with a list of clothing items.
     * Built Well: Uses [withTransaction] to ensure the outfit and its items are created atomically.
     * @param name The display name of the outfit.
     * @param notes Optional notes for the outfit.
     * @param itemIds The list of clothing item IDs to include in the outfit.
     * @return The unique ID of the newly created outfit.
     */
    suspend fun createOutfit(name: String?, notes: String?, itemIds: List<Long>): Long = database.withTransaction {
        val outfitId = outfitDao.insertOutfit(OutfitEntity(name = name, notes = notes))
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
        outfitId
    }

    /**
     * Updates an existing outfit's details and replaces its item associations.
     * Built Well: Uses [withTransaction] to ensure the replacement of associations is atomic.
     * @param outfitId The ID of the outfit to update.
     * @param name The updated name.
     * @param notes The updated notes.
     * @param itemIds The new list of clothing item IDs for the outfit.
     */
    suspend fun updateOutfit(outfitId: Long, name: String?, notes: String?, itemIds: List<Long>) = database.withTransaction {
        outfitDao.updateOutfit(OutfitEntity(id = outfitId, name = name, notes = notes))
        outfitDao.deleteItemsForOutfit(outfitId)
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
    }

    /**
     * Deletes an outfit and all its constituent item associations.
     * @param outfitId The ID of the outfit to delete.
     */
    suspend fun deleteOutfit(outfitId: Long) {
        outfitDao.deleteOutfitById(outfitId)
    }

    /**
     * Retrieves all outfits that contain a specific clothing item.
     * @param itemId The ID of the clothing item.
     * @return A [Flow] emitting a list of [OutfitWithMeta].
     */
    fun getOutfitsForItem(itemId: Long): Flow<List<OutfitWithMeta>> = 
        outfitDao.getOutfitsForItem(itemId)
}
