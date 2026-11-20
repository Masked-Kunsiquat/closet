package com.closet.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.Converters
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.*
import com.closet.core.data.time.AppClock
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.isConstraintViolation
import com.closet.core.data.util.ItemVectorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

/**
 * Repository providing access to clothing items.
 * Parity: This is the native equivalent of hooks/useClothingItems.ts.
 * Built Well: Uses database transactions for atomic junction updates and wraps results in [DataResult].
 */
class ClothingRepository(
    private val database: ClothingDatabase,
    private val clothingDao: ClothingDao,
    private val clock: AppClock,
) {
    private val converters = Converters()

    // Singleton scope for fire-and-forget work (e.g. vectorization after insert/update).
    // SupervisorJob ensures one failed launch doesn't cancel the others.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        Logger.withTag("ClothingRepository").e(e) { "Error fetching item by ID: $id" }
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
        Logger.withTag("ClothingRepository").e(e) { "Error fetching entity by ID: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * One-shot fetch of a fully-loaded [ClothingItemDetail] including all junction data.
     * Returns [DataResult.Success] with a null value when the item does not exist.
     */
    suspend fun getItemDetailOnce(id: Long): DataResult<ClothingItemDetail?> = try {
        DataResult.Success(clothingDao.getClothingItemDetailOnce(id))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error fetching item detail once for ID: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Retrieves the colors associated with a clothing item.
     */
    fun getItemColors(itemId: Long): Flow<List<ColorEntity>> = clothingDao.getItemColors(itemId)

    /**
     * One-shot fetch of an item's raw color rows, with each row's measured hex, share and source.
     */
    suspend fun getItemColorRows(itemId: Long): DataResult<List<ClothingItemColorEntity>> = try {
        DataResult.Success(clothingDao.getItemColorRows(itemId))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error fetching color rows for item: $itemId" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Inserts a new clothing item along with its associations.
     * @param item The item entity to insert.
     * @param colors Optional colors to associate.
     * @return A [DataResult] containing the new row ID.
     */
    suspend fun insertItem(
        item: ClothingItemEntity,
        colors: List<TaggedColor> = emptyList()
    ): DataResult<Long> = wrapInTransaction {
        val id = clothingDao.insertClothingItem(item)
        if (colors.isNotEmpty()) {
            clothingDao.updateItemColors(id, colors)
        }
        id
    }

    /**
     * Inserts a new clothing item along with its associated colors.
     * Best-effort: runs ItemVectorizer after a successful insert to populate semantic_description.
     */
    suspend fun insertItemWithColors(
        item: ClothingItemEntity,
        colors: List<TaggedColor>
    ): DataResult<Long> {
        val result = insertItem(item, colors)
        if (result is DataResult.Success) repositoryScope.launch { vectorizeItem(result.data) }
        return result
    }

    /**
     * Inserts a new clothing item with all junction table associations in a single transaction.
     * Best-effort: runs ItemVectorizer after a successful insert.
     */
    suspend fun insertItemWithAttributes(
        item: ClothingItemEntity,
        colors: List<TaggedColor>,
        seasonIds: List<Long>,
        occasionIds: List<Long>,
        materialIds: List<Long>,
        patternIds: List<Long>,
    ): DataResult<Long> {
        val result = wrapInTransaction {
            val id = clothingDao.insertClothingItem(item)
            clothingDao.updateItemColors(id, colors)
            clothingDao.updateItemSeasons(id, seasonIds)
            clothingDao.updateItemOccasions(id, occasionIds)
            clothingDao.updateItemMaterials(id, materialIds)
            clothingDao.updateItemPatterns(id, patternIds)
            id
        }
        if (result is DataResult.Success) repositoryScope.launch { vectorizeItem(result.data) }
        return result
    }

    /**
     * Updates an existing clothing item with all junction table associations in a single transaction.
     * Best-effort: re-runs ItemVectorizer after a successful update.
     */
    suspend fun updateItemWithAttributes(
        item: ClothingItemEntity,
        colors: List<TaggedColor>,
        seasonIds: List<Long>,
        occasionIds: List<Long>,
        materialIds: List<Long>,
        patternIds: List<Long>,
    ): DataResult<Int> {
        val result = wrapInTransaction {
            val rowsAffected = clothingDao.updateClothingItem(item)
            if (rowsAffected == 0) return@wrapInTransaction 0
            clothingDao.updateItemColors(item.id, colors)
            clothingDao.updateItemSeasons(item.id, seasonIds)
            clothingDao.updateItemOccasions(item.id, occasionIds)
            clothingDao.updateItemMaterials(item.id, materialIds)
            clothingDao.updateItemPatterns(item.id, patternIds)
            rowsAffected
        }
        return if (result is DataResult.Success && result.data == 0) {
            DataResult.Error(AppError.DatabaseError.NotFound())
        } else {
            result
        }.also { if (it is DataResult.Success) repositoryScope.launch { vectorizeItem(item.id) } }
    }

    /**
     * Updates an existing clothing item and its associations.
     * @param item The item entity with updated values.
     * @param colors Colors to replace the item's color rows with; null leaves them unchanged.
     * @return A [DataResult] containing the number of rows updated.
     */
    suspend fun updateItem(
        item: ClothingItemEntity,
        colors: List<TaggedColor>? = null
    ): DataResult<Int> {
        val result = wrapInTransaction {
            val rowsAffected = clothingDao.updateClothingItem(item)
            if (rowsAffected == 0) return@wrapInTransaction 0
            if (colors != null) {
                clothingDao.updateItemColors(item.id, colors)
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
     * Best-effort: re-runs ItemVectorizer after a successful update to refresh semantic_description.
     */
    suspend fun updateItemWithColors(
        item: ClothingItemEntity,
        colors: List<TaggedColor>
    ): DataResult<Int> {
        val result = updateItem(item, colors)
        if (result is DataResult.Success) repositoryScope.launch { vectorizeItem(item.id) }
        return result
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
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error deleting item: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Updates the wash status for a specific clothing item.
     * @param id The ID of the item.
     * @param washStatus The new [WashStatus].
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateWashStatus(id: Long, washStatus: WashStatus): DataResult<Unit> = try {
        val updatedAt = converters.dateToTimestamp(clock.now()) ?: ""
        clothingDao.updateWashStatus(id, washStatus.label, updatedAt)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error updating wash status for item: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Updates the lifecycle status for a specific clothing item.
     * @param id The ID of the item.
     * @param status The new [ClothingStatus].
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateStatus(id: Long, status: ClothingStatus): DataResult<Unit> = try {
        val updatedAt = converters.dateToTimestamp(clock.now()) ?: ""
        clothingDao.updateItemStatus(id, status.label, updatedAt)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error updating status for item: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Toggles the favorite status for a specific clothing item.
     * @param id The ID of the item.
     * @param isFavorite Whether the item should be marked as favorite.
     * @return A [DataResult] indicating success or failure.
     */
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean): DataResult<Unit> = try {
        val updatedAt = converters.dateToTimestamp(clock.now()) ?: ""
        clothingDao.updateFavoriteStatus(id, if (isFavorite) 1 else 0, updatedAt)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Error updating favorite status for item: $id" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    // --- Junction Table Management ---
    // Atomic updates in an immediate write transaction to ensure data integrity.

    /**
     * Atomically replaces color associations for an item.
     * @param itemId The ID of the clothing item.
     * @param colors The new color rows.
     */
    suspend fun updateItemColors(itemId: Long, colors: List<TaggedColor>): DataResult<Unit> = wrapInTransaction {
        clothingDao.updateItemColors(itemId, colors)
    }

    /**
     * Replaces an item's colors with freshly [extracted] ones as [ColorSource.AUTO] rows. Nothing is
     * written when the user has curated them (any existing row has source [ColorSource.USER]) or
     * when [extracted] is empty (the extractor found no colour), so a failed extraction never
     * wipes the existing rows. The check and the write run in one transaction.
     * [ColorSource.NANO] rows are replaceable like auto rows, intentionally: Nano tagging arrives in PR 3.
     * @return A [DataResult] with true when the rows were replaced, false when they were left
     *   unchanged (user-curated, or nothing extracted).
     */
    suspend fun replaceAutoColors(itemId: Long, extracted: List<ExtractedColor>): DataResult<Boolean> = wrapInTransaction {
        if (extracted.isEmpty()) return@wrapInTransaction false
        val curated = clothingDao.getItemColorRows(itemId).any { it.source == ColorSource.USER }
        if (curated) return@wrapInTransaction false
        clothingDao.updateItemColors(
            itemId,
            extracted.map { TaggedColor(it.colorId, hex = it.hex, share = it.share, source = ColorSource.AUTO) },
        )
        true
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
     * Public entry point for background workers (e.g. [com.closet.features.wardrobe.BatchSegmentationWorker])
     * that need to refresh the semantic description after updating an item's image.
     *
     * Returns [Unit] intentionally: [vectorizeItem] already catches and logs all non-cancellation
     * exceptions, so there is no observable error state to surface. Callers should treat this as
     * fire-and-forget (failures are visible in Logcat under the [ClothingRepository] tag).
     */
    suspend fun revectorizeItem(id: Long) = vectorizeItem(id)

    /**
     * Fetches the full [ClothingItemDetail] for [id] and writes its prose description
     * to [com.closet.core.data.dao.ClothingDao.updateSemanticDescription].
     * Failures are logged and swallowed — vectorization is best-effort and must never
     * block or roll back the main save operation.
     */
    private suspend fun vectorizeItem(id: Long) {
        try {
            val detail = clothingDao.getClothingItemDetailOnce(id) ?: return
            val description = ItemVectorizer.describe(detail)
            clothingDao.updateSemanticDescription(id, description, clock.now())
            Logger.withTag("ClothingRepository").d { "vectorizeItem: wrote description for item $id (${description.length} chars)" }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.withTag("ClothingRepository").d(e) { "vectorizeItem: failed for item $id — ignored" }
        }
    }

    /**
     * Helper to wrap a database transaction block in [DataResult] with error handling.
     */
    private suspend fun <T> wrapInTransaction(block: suspend () -> T): DataResult<T> = try {
        DataResult.Success(database.useWriterConnection { connection -> connection.immediateTransaction { block() } })
    } catch (e: SQLiteException) {
        if (e.isConstraintViolation()) {
            Logger.withTag("ClothingRepository").e(e) { "Database transaction constraint violation" }
            DataResult.Error(AppError.DatabaseError.ConstraintViolation("Database constraint violated"))
        } else {
            Logger.withTag("ClothingRepository").e(e) { "Unexpected error in database transaction" }
            DataResult.Error(AppError.DatabaseError.QueryError(e))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.withTag("ClothingRepository").e(e) { "Unexpected error in database transaction" }
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
