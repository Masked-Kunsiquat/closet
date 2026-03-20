package com.closet.core.data.repository

import androidx.room.withTransaction
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.Converters
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.*
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import java.time.Instant

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
    private val converters = Converters()

    /**
     * Retrieves all clothing items as a stream.
     * @return A [Flow] emitting a list of [ClothingItemWithMeta].
     */
    fun getAllItems(): Flow<List<ClothingItemWithMeta>> = clothingDao.getAllClothingItems()

    /**
     * Retrieves all clothing items with full associations for advanced filtering and listing.
     * @return A [Flow] emitting a list of [ClothingItemDetail].
     */
    fun getAllItemDetails(): Flow<List<ClothingItemDetail>> = clothingDao.getAllClothingItemDetails()

    /**
     * Retrieves a single clothing item with meta by its unique ID.
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
        if (e is CancellationException) throw e
        Timber.e(e, "Error fetching item by ID: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Retrieves the detailed clothing item with all associations by its unique ID.
     * @param id The ID of the clothing item.
     * @return A [Flow] emitting the [ClothingItemDetail] if found.
     */
    fun getItemDetail(id: Long): Flow<ClothingItemDetail?> = clothingDao.getClothingItemDetail(id)

    /**
     * Retrieves the raw clothing item entity by its unique ID.
     * @param id The ID of the clothing item.
     * @return A [DataResult] containing the entity or an error if not found.
     */
    suspend fun getItemEntityById(id: Long): DataResult<ClothingItemEntity> = try {
        val item = clothingDao.getClothingItemEntityById(id)
        if (item != null) {
            DataResult.Success(item)
        } else {
            DataResult.Error(AppError.DatabaseError.NotFound())
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error fetching entity by ID: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Retrieves the colors associated with a clothing item.
     */
    fun getItemColors(itemId: Long): Flow<List<ColorEntity>> = clothingDao.getItemColors(itemId)

    /**
     * Inserts a new clothing item along with its associations.
     * @param item The item entity to insert.
     * @param colorIds Optional list of color IDs to associate.
     * @return A [DataResult] containing the new row ID.
     */
    suspend fun insertItem(
        item: ClothingItemEntity,
        colorIds: List<Long> = emptyList()
    ): DataResult<Long> = wrapInTransaction {
        val id = clothingDao.insertClothingItem(item)
        if (colorIds.isNotEmpty()) {
            clothingDao.updateItemColors(id, colorIds)
        }
        id
    }

    /**
     * Inserts a new clothing item along with its associated colors.
     */
    suspend fun insertItemWithColors(
        item: ClothingItemEntity,
        colors: List<ColorEntity>
    ): DataResult<Long> = insertItem(item, colors.map { it.id })

    /**
     * Updates an existing clothing item and its associations.
     * @param item The item entity with updated values.
     * @param colorIds Optional list of color IDs to associate.
     * @return A [DataResult] containing the number of rows updated.
     */
    suspend fun updateItem(
        item: ClothingItemEntity,
        colorIds: List<Long>? = null
    ): DataResult<Int> {
        val result = wrapInTransaction {
            val rowsAffected = clothingDao.updateClothingItem(item)
            if (rowsAffected == 0) return@wrapInTransaction 0
            if (colorIds != null) {
                clothingDao.updateItemColors(item.id, colorIds)
            }
            rowsAffected
        }
        return if (result is DataResult.Success && result.data == 0) {
            DataResult.Error(AppError.DatabaseError.NotFound())
        } else {
            result
        }
    }

    /**
     * Updates an existing clothing item and its associated colors.
     */
    suspend fun updateItemWithColors(
        item: ClothingItemEntity,
        colors: List<ColorEntity>
    ): DataResult<Int> = updateItem(item, colors.map { it.id })

    /**
     * Deletes a clothing item by its ID.
     * @param id The ID of the item to delete.
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun deleteItem(id: Long): DataResult<Unit> = try {
        clothingDao.deleteClothingItem(id)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
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
        val updatedAt = converters.dateToTimestamp(Instant.now()) ?: ""
        clothingDao.updateWashStatus(id, washStatus.label, updatedAt)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error updating wash status for item: $id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Toggles the favorite status for a specific clothing item.
     * @param id The ID of the item.
     * @param isFavorite Whether the item should be marked as favorite.
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean): DataResult<Unit> = try {
        val updatedAt = converters.dateToTimestamp(Instant.now()) ?: ""
        clothingDao.updateFavoriteStatus(id, if (isFavorite) 1 else 0, updatedAt)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error updating favorite status for item: $id")
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
        clothingDao.updateItemColors(itemId, colorIds)
    }

    /**
     * Atomically replaces material associations for an item.
     * @param itemId The ID of the clothing item.
     * @param materialIds The list of new material IDs.
     */
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.updateItemMaterials(itemId, materialIds)
    }

    /**
     * Atomically replaces season associations for an item.
     * @param itemId The ID of the clothing item.
     * @param seasonIds The list of new season IDs.
     */
    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.updateItemSeasons(itemId, seasonIds)
    }

    /**
     * Atomically replaces occasion associations for an item.
     * @param itemId The ID of the clothing item.
     * @param occasionIds The list of new occasion IDs.
     */
    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.updateItemOccasions(itemId, occasionIds)
    }

    /**
     * Atomically replaces pattern associations for an item.
     * @param itemId The ID of the clothing item.
     * @param patternIds The list of new pattern IDs.
     */
    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>): DataResult<Unit> = wrapInTransaction {
        clothingDao.updateItemPatterns(itemId, patternIds)
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
        if (e is CancellationException) throw e
        Timber.e(e, "Unexpected error in database transaction")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
