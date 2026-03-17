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
 * Mirror of Outfit queries in hangr/db/queries.ts.
 */
@Singleton
class OutfitRepository @Inject constructor(
    private val database: ClothingDatabase,
    private val outfitDao: OutfitDao
) {
    /**
     * Fetch all outfits with item count and cover image.
     */
    fun getAllOutfits(): Flow<List<OutfitWithMeta>> = outfitDao.getAllOutfits()

    /**
     * Create a new outfit and associate it with clothing items.
     * Built Well: Uses withTransaction for atomic creation.
     */
    suspend fun createOutfit(name: String?, notes: String?, itemIds: List<Long>): Long = database.withTransaction {
        val outfitId = outfitDao.insertOutfit(OutfitEntity(name = name, notes = notes))
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
        outfitId
    }

    /**
     * Update an outfit and replace its items.
     * Built Well: Uses withTransaction for atomic replacement.
     */
    suspend fun updateOutfit(outfitId: Long, name: String?, notes: String?, itemIds: List<Long>) = database.withTransaction {
        outfitDao.updateOutfit(OutfitEntity(id = outfitId, name = name, notes = notes))
        outfitDao.deleteItemsForOutfit(outfitId)
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
    }

    /**
     * Delete an outfit and its associations.
     */
    suspend fun deleteOutfit(outfitId: Long) {
        outfitDao.deleteOutfitById(outfitId)
    }

    /**
     * Get outfits containing a specific item.
     */
    fun getOutfitsForItem(itemId: Long): Flow<List<OutfitWithMeta>> = 
        outfitDao.getOutfitsForItem(itemId)
}
