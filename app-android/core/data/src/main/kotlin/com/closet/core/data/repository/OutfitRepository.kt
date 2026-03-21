package com.closet.core.data.repository

import androidx.room.withTransaction
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.OutfitDao
import com.closet.core.data.model.*
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model for an Outfit with its constituent items and layout metadata.
 */
data class Outfit(
    val id: Long,
    val name: String?,
    val notes: String?,
    val items: List<OutfitItem>
)

/**
 * Domain model for a clothing item within an outfit, including layout metadata.
 */
data class OutfitItem(
    val clothingItem: ClothingItemEntity,
    val posX: Float?,
    val posY: Float?,
    val scale: Float?,
    val zIndex: Int?
)

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
     * Retrieves all outfits with their full item list and layout metadata.
     * Used by the gallery to power [OutfitPreview]'s collage-or-grid logic.
     */
    fun getAllOutfitsWithItems(): Flow<List<OutfitWithItems>> = outfitDao.getAllOutfitsWithItems()

    /**
     * Retrieves a full outfit by its ID, including all items and layout metadata.
     */
    fun getOutfitById(id: Long): Flow<Outfit?> =
        outfitDao.getOutfitWithItems(id).map { it?.toDomain() }

    /**
     * Creates a new outfit and associates it with a list of clothing items.
     * Built Well: Uses [withTransaction] to ensure the outfit and its items are created atomically.
     * @param name The display name of the outfit.
     * @param notes Optional notes for the outfit.
     * @param itemIds The list of clothing item IDs to include in the outfit.
     * @return The unique ID of the newly created outfit wrapped in [DataResult].
     */
    suspend fun createOutfit(name: String?, notes: String?, itemIds: List<Long>): DataResult<Long> = try {
        val outfitId = database.withTransaction {
            val id = outfitDao.insertOutfit(OutfitEntity(name = name, notes = notes))
            val outfitItems = itemIds.mapIndexed { index, itemId ->
                OutfitItemEntity(outfitId = id, clothingItemId = itemId, zIndex = index)
            }
            outfitDao.insertOutfitItems(outfitItems)
            id
        }
        DataResult.Success(outfitId)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error creating outfit")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Updates an existing outfit's details and replaces its item associations.
     * Built Well: Uses [withTransaction] to ensure the replacement of associations is atomic.
     * @param outfitId The ID of the outfit to update.
     * @param name The updated name.
     * @param notes The updated notes.
     * @param items The new list of outfit items with their layout metadata.
     */
    suspend fun updateOutfit(
        outfitId: Long,
        name: String?,
        notes: String?,
        items: List<OutfitItem>
    ): DataResult<Unit> = try {
        database.withTransaction {
            val updatedRows = outfitDao.updateOutfitFields(
                id = outfitId,
                name = name,
                notes = notes,
                updatedAt = Instant.now()
            )
            
            if (updatedRows == 0) {
                throw AppError.DatabaseError.NotFound()
            }

            outfitDao.deleteItemsForOutfit(outfitId)
            val outfitItems = items.map { 
                OutfitItemEntity(
                    outfitId = outfitId,
                    clothingItemId = it.clothingItem.id,
                    posX = it.posX,
                    posY = it.posY,
                    scale = it.scale,
                    zIndex = it.zIndex
                )
            }
            outfitDao.insertOutfitItems(outfitItems)
        }
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error updating outfit: $outfitId")
        if (e is AppError) {
            DataResult.Error(e)
        } else {
            DataResult.Error(AppError.DatabaseError.QueryError(e))
        }
    }

    /**
     * Deletes an outfit and all its constituent item associations.
     * @param outfitId The ID of the outfit to delete.
     */
    suspend fun deleteOutfit(outfitId: Long): DataResult<Unit> = try {
        outfitDao.deleteOutfitById(outfitId)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error deleting outfit: $outfitId")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Retrieves all outfits that contain a specific clothing item.
     * @param itemId The ID of the clothing item.
     * @return A [Flow] emitting a list of [OutfitWithMeta].
     */
    fun getOutfitsForItem(itemId: Long): Flow<List<OutfitWithMeta>> = 
        outfitDao.getOutfitsForItem(itemId)
}

private fun OutfitWithItems.toDomain(): Outfit {
    return Outfit(
        id = outfit.id,
        name = outfit.name,
        notes = outfit.notes,
        items = items.sortedBy { it.outfitItem.zIndex }.map {
            OutfitItem(
                clothingItem = it.clothingItem,
                posX = it.outfitItem.posX,
                posY = it.outfitItem.posY,
                scale = it.outfitItem.scale,
                zIndex = it.outfitItem.zIndex
            )
        }
    )
}
