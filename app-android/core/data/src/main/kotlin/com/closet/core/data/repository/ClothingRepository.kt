package com.closet.core.data.repository

import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingItemWithMeta
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
}
