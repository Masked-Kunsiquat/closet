package com.closet.core.data.repository

import androidx.room.withTransaction
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing access to clothing items.
 * Parity: This is the native equivalent of hooks/useClothingItems.ts.
 * Built Well: Uses database transactions for atomic junction updates.
 */
@Singleton
class ClothingRepository @Inject constructor(
    private val database: ClothingDatabase,
    private val clothingDao: ClothingDao
) {
    fun getAllItems(): Flow<List<ClothingItemWithMeta>> = clothingDao.getAllClothingItems()

    suspend fun getItemById(id: Long): ClothingItemWithMeta? = clothingDao.getClothingItemById(id)

    suspend fun insertItem(item: ClothingItemEntity): Long = clothingDao.insertClothingItem(item)

    suspend fun updateItem(item: ClothingItemEntity) = clothingDao.updateClothingItem(item)

    suspend fun deleteItem(id: Long) = clothingDao.deleteClothingItem(id)

    suspend fun updateWashStatus(id: Long, washStatus: WashStatus) = 
        clothingDao.updateWashStatus(id, washStatus.label)

    // --- Junction Table Management ---
    // Atomic updates using withTransaction to ensure data integrity.

    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) = database.withTransaction {
        clothingDao.deleteItemColors(itemId)
        clothingDao.insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) = database.withTransaction {
        clothingDao.deleteItemMaterials(itemId)
        clothingDao.insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>) = database.withTransaction {
        clothingDao.deleteItemSeasons(itemId)
        clothingDao.insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>) = database.withTransaction {
        clothingDao.deleteItemOccasions(itemId)
        clothingDao.insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>) = database.withTransaction {
        clothingDao.deleteItemPatterns(itemId)
        clothingDao.insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }
}
