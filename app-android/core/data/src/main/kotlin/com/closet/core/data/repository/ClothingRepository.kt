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
 * Built Well: Uses database transactions for atomic junction updates and wraps results in [DataResult].
 */
@Singleton
class ClothingRepository @Inject constructor(
    private val database: ClothingDatabase,
    private val clothingDao: ClothingDao
) {
    /**
     * Retrieves all clothing items as a stream.
     * @return A [Flow] emitting a list of [ClothingItemWithMeta].
     */
    fun getAllItems(): Flow<List<ClothingItemWithMeta>> = clothingDao.getAllClothingItems()

    /**
     * Retrieves a single clothing item by its unique ID.
     * @param id The ID of the clothing item.
     * @return A [DataResult] containing the item or an error if not found.
     */
    suspend fun getItemById(id: Long): DataResult<ClothingItemWithMeta> = try {
        val item = clothingDao.getClothingItemById(id)
        if (item != null) {
            DataResult.Success(item)
        } else {
            DataResult.Error(AppError.DatabaseError.NotFound())
        }
    } catch (e: Exception) {
        Timber.e(e, "Error fetching item by ID: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Inserts a new clothing item.
     * @param item The item entity to insert.
     * @return A [DataResult] containing the new row ID.
     */
    suspend fun insertItem(item: ClothingItemEntity): DataResult<Long> = try {
        val id = clothingDao.insertClothingItem(item)
        DataResult.Success(id)
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
        Timber.e(e, "Constraint violation inserting item")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation("Database constraint violated"))
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error inserting item")
        DataResult.Error(AppError.Unexpected(e))
    }

    /**
     * Updates an existing clothing item.
     * @param item The item entity with updated values.
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateItem(item: ClothingItemEntity): DataResult<Unit> = try {
        clothingDao.updateClothingItem(item)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error updating item: ${item.id}")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Deletes a clothing item by its ID.
     * @param id The ID of the item to delete.
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun deleteItem(id: Long): DataResult<Unit> = try {
        clothingDao.deleteClothingItem(id)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error deleting item: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Updates the wash status for a specific clothing item.
     * @param id The ID of the item.
     * @param washStatus The new [WashStatus].
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateWashStatus(id: Long, washStatus: WashStatus): DataResult<Unit> = try {
        clothingDao.updateWashStatus(id, washStatus.label)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Error updating wash status for item: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    // --- Junction Table Management ---
    // Atomic updates using withTransaction to ensure data integrity.

    /**
     * Atomically replaces color associations for an item.
     * @param itemId The ID of the clothing item.
     * @param colorIds The list of new color IDs.
     */
    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemColors(itemId)
        clothingDao.insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    /**
     * Atomically replaces material associations for an item.
     * @param itemId The ID of the clothing item.
     * @param materialIds The list of new material IDs.
     */
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemMaterials(itemId)
        clothingDao.insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    /**
     * Atomically replaces season associations for an item.
     * @param itemId The ID of the clothing item.
     * @param seasonIds The list of new season IDs.
     */
    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemSeasons(itemId)
        clothingDao.insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    /**
     * Atomically replaces occasion associations for an item.
     * @param itemId The ID of the clothing item.
     * @param occasionIds The list of new occasion IDs.
     */
    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemOccasions(itemId)
        clothingDao.insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    /**
     * Atomically replaces pattern associations for an item.
     * @param itemId The ID of the clothing item.
     * @param patternIds The list of new pattern IDs.
     */
    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.deleteItemPatterns(itemId)
        clothingDao.insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }

    /**
     * Helper to wrap a database transaction block in [DataResult] with error handling.
     */
    private suspend fun <T> wrapInTransaction(block: suspend () -> T): DataResult<T> = try {
        DataResult.Success(database.withTransaction { block() })
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
        Timber.e(e, "Database transaction constraint violation")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation("Database constraint violated"))
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error in database transaction")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
