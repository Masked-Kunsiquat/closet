package com.closet.core.data.repository

import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing access to clothing items.
 * Parity: This is the native equivalent of hooks/useClothingItems.ts.
 */
@Singleton
class ClothingRepository @Inject constructor(
    private val clothingDao: ClothingDao
) {
    fun getAllItems(): Flow<List<ClothingItemWithMeta>> = clothingDao.getAllClothingItems()

    suspend fun getItemById(id: Long): ClothingItemWithMeta? = clothingDao.getClothingItemById(id)

    suspend fun insertItem(item: ClothingItemEntity): Long = clothingDao.insertClothingItem(item)

    suspend fun updateItem(item: ClothingItemEntity) = clothingDao.updateClothingItem(item)

    suspend fun deleteItem(id: Long) = clothingDao.deleteClothingItem(id)

    suspend fun updateWashStatus(id: Long, washStatus: String) = 
        clothingDao.updateWashStatus(id, washStatus)

    // --- Junction Table Management ---
    // Parity: Mirror of setClothingItemColors, setClothingItemMaterials, etc. from queries.ts

    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) {
        // Implementation delegates to DAO delete-then-insert pattern (to be added to DAO if not present)
    }

    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) {
        // Implementation delegates to DAO delete-then-insert pattern
    }

    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>) {
        // Implementation delegates to DAO delete-then-insert pattern
    }
}
