package com.closet.core.data.repository

import androidx.room.Transaction
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
    private val outfitDao: OutfitDao
) {
    /**
     * Fetch all outfits with item count and cover image.
     */
    fun getAllOutfits(): Flow<List<OutfitWithMeta>> = outfitDao.getAllOutfits()

    /**
     * Create a new outfit and associate it with clothing items.
     * Parity: Atomic insert with transaction.
     */
    @Transaction
    suspend fun createOutfit(name: String?, notes: String?, itemIds: List<Long>): Long {
        val outfitId = outfitDao.insertOutfit(OutfitEntity(name = name, notes = notes))
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
        return outfitId
    }

    /**
     * Update an outfit and replace its items.
     */
    @Transaction
    suspend fun updateOutfit(outfitId: Long, name: String?, notes: String?, itemIds: List<Long>) {
        outfitDao.updateOutfit(OutfitEntity(id = outfitId, name = name, notes = notes))
        outfitDao.deleteItemsForOutfit(outfitId)
        val outfitItems = itemIds.map { OutfitItemEntity(outfitId, it) }
        outfitDao.insertOutfitItems(outfitItems)
    }

    /**
     * Delete an outfit and its associations.
     */
    suspend fun deleteOutfit(outfitId: Long) {
        outfitDao.deleteOutfit(OutfitEntity(id = outfitId))
    }

    /**
     * Get outfits containing a specific item.
     */
    fun getOutfitsForItem(itemId: Long): Flow<List<OutfitWithMeta>> = 
        outfitDao.getOutfitsForItem(itemId)
}
