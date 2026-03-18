package com.closet.core.data.repository

import androidx.room.withTransaction
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.*
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
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

    suspend fun getItemById(id: Long): DataResult<ClothingItemWithMeta> = try {
        val item = clothingDao.getClothingItemById(id)
        if (item != null) {
            DataResult.Success(item)
        } else {
            DataResult.Error(AppError.DatabaseError.NotFound)
        }
    } catch (e: Exception) {
        Timber.e(e, "Error fetching item by ID: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun insertItem(item: ClothingItemEntity): DataResult<Long> = try {
        val id = clothingDao.insertClothingItem(item)
        DataResult.Success(id)
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
        Timber.e(e, "Constraint violation inserting item")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation(e.message ?: "Unknown constraint"))
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error inserting item")
        DataResult.Error(AppError.Unexpected(e))
    }

    suspend fun updateItem(item: ClothingItemEntity): DataResult<Unit> = try {
        clothingDao.updateClothingItem(item)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error updating item: ${item.id}")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun deleteItem(id: Long): DataResult<Unit> = try {
        clothingDao.deleteClothingItem(id)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error deleting item: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun updateWashStatus(id: Long, washStatus: WashStatus): DataResult<Unit> = try {
        clothingDao.updateWashStatus(id, washStatus.label)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error updating wash status for item: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    // --- Junction Table Management ---
    // Atomic updates using withTransaction to ensure data integrity.

    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemColors(itemId)
        clothingDao.insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemMaterials(itemId)
        clothingDao.insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemSeasons(itemId)
        clothingDao.insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemOccasions(itemId)
        clothingDao.insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemPatterns(itemId)
        clothingDao.insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }

    private suspend fun <T> wrapInTransaction(block: suspend () -> T): DataResult<T> = try {
        DataResult.Success(database.withTransaction { block() })
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
        Timber.e(e, "Database transaction constraint violation")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation(e.message ?: "Unknown constraint"))
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error in database transaction")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
