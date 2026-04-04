package com.closet.core.data.dao

import androidx.room.*
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Correlated subquery that counts distinct outfit log entries for a clothing item.
 * Joins via [outfit_log_items] (the historical snapshot table) rather than [outfit_items]
 * (live outfit membership) so that:
 *  - ad-hoc single-item wear logs (outfit_id = null) are included, and
 *  - items removed from an outfit after logging still count as worn.
 * Requires the `ci` alias to be in scope (clothing_items). Alias `wear_count` is included.
 */
private const val WEAR_COUNT_SUBQUERY = """(
        SELECT COUNT(DISTINCT ol.id)
        FROM outfit_logs ol
        JOIN outfit_log_items oli ON oli.outfit_log_id = ol.id
        WHERE oli.clothing_item_id = ci.id
    ) AS wear_count"""

/**
 * Data Access Object for clothing items and their associated metadata.
 * Provides methods for querying, inserting, updating, and deleting items.
 * Parity: This is the native equivalent of queries.ts from the Expo project.
 */
@Dao
interface ClothingDao {

    /**
     * Retrieves all clothing items with their category, subcategory, and wear count.
     * Items are ordered by creation date in descending order (newest first).
     * @return A [Flow] emitting a list of [ClothingItemWithMeta].
     */
    @Query("""
        SELECT
            ci.id, ci.name, b.name AS brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            """ + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
        LEFT JOIN brands        b  ON ci.brand_id       = b.id
        ORDER BY ci.created_at DESC
    """)
    fun getAllClothingItems(): Flow<List<ClothingItemWithMeta>>

    /**
     * Retrieves a single clothing item with meta by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return The [ClothingItemWithMeta] if found, null otherwise.
     */
    @Query("""
        SELECT
            ci.id, ci.name, b.name AS brand, ci.image_path, ci.purchase_price, ci.status, ci.is_favorite, ci.wash_status,
            c.name  AS category_name,
            sc.name AS subcategory_name,
            """ + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        LEFT JOIN categories    c  ON ci.category_id    = c.id
        LEFT JOIN subcategories sc ON ci.subcategory_id = sc.id
        LEFT JOIN brands        b  ON ci.brand_id       = b.id
        WHERE ci.id = :id
    """)
    suspend fun getClothingItemById(id: Long): ClothingItemWithMeta?

    /**
     * Retrieves all clothing items with full associations for filtering and listing.
     * @return A [Flow] emitting a list of [ClothingItemDetail].
     */
    @Transaction
    @Query("SELECT ci.*, " + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        ORDER BY ci.created_at DESC
    """)
    fun getAllClothingItemDetails(): Flow<List<ClothingItemDetail>>

    /**
     * Retrieves the detailed clothing item with all associations by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return A [Flow] emitting the [ClothingItemDetail] if found.
     */
    @Transaction
    @Query("SELECT ci.*, " + WEAR_COUNT_SUBQUERY + """
        FROM clothing_items ci
        WHERE ci.id = :id
    """)
    fun getClothingItemDetail(id: Long): Flow<ClothingItemDetail?>

    /**
     * Retrieves the raw clothing item entity by its unique ID.
     * @param id The ID of the clothing item to retrieve.
     * @return The [ClothingItemEntity] if found, null otherwise.
     */
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getClothingItemEntityById(id: Long): ClothingItemEntity?

    /**
     * Retrieves the colors associated with a clothing item.
     */
    @Query("""
        SELECT c.* FROM colors c
        JOIN clothing_item_colors cic ON c.id = cic.color_id
        WHERE cic.clothing_item_id = :itemId
    """)
    fun getItemColors(itemId: Long): Flow<List<ColorEntity>>

    /**
     * Inserts a new clothing item into the database.
     * @param item The [ClothingItemEntity] to insert.
     * @return The row ID of the newly inserted item.
     */
    @Insert
    suspend fun insertClothingItem(item: ClothingItemEntity): Long

    /**
     * Updates an existing clothing item in the database.
     * @param item The [ClothingItemEntity] with updated values.
     * @return The number of rows affected.
     */
    @Update
    suspend fun updateClothingItem(item: ClothingItemEntity): Int

    /**
     * Deletes a clothing item from the database by its ID.
     */
    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingItem(id: Long)
    
    /**
     * Updates the wash status and modification timestamp for a specific clothing item.
     */
    @Query("UPDATE clothing_items SET wash_status = :washStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateWashStatus(id: Long, washStatus: String, updatedAt: String)

    /**
     * Updates the favorite status and modification timestamp for a specific clothing item.
     */
    @Query("UPDATE clothing_items SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Int, updatedAt: String)

    /**
     * Updates the lifecycle status and modification timestamp for a specific clothing item.
     */
    @Query("UPDATE clothing_items SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateItemStatus(id: Long, status: String, updatedAt: String)

    // --- Junction Table Helpers (Atomically replace associations) ---

    /**
     * Updates the color associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemColors(itemId: Long, colorIds: List<Long>) {
        deleteItemColors(itemId)
        insertItemColors(colorIds.map { ClothingItemColorEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_colors WHERE clothing_item_id = :itemId")
    suspend fun deleteItemColors(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemColors(items: List<ClothingItemColorEntity>)

    /**
     * Updates the material associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemMaterials(itemId: Long, materialIds: List<Long>) {
        deleteItemMaterials(itemId)
        insertItemMaterials(materialIds.map { ClothingItemMaterialEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_materials WHERE clothing_item_id = :itemId")
    suspend fun deleteItemMaterials(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemMaterials(items: List<ClothingItemMaterialEntity>)

    /**
     * Updates the season associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemSeasons(itemId: Long, seasonIds: List<Long>) {
        deleteItemSeasons(itemId)
        insertItemSeasons(seasonIds.map { ClothingItemSeasonEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_seasons WHERE clothing_item_id = :itemId")
    suspend fun deleteItemSeasons(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemSeasons(items: List<ClothingItemSeasonEntity>)

    /**
     * Updates the occasion associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemOccasions(itemId: Long, occasionIds: List<Long>) {
        deleteItemOccasions(itemId)
        insertItemOccasions(occasionIds.map { ClothingItemOccasionEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_occasions WHERE clothing_item_id = :itemId")
    suspend fun deleteItemOccasions(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemOccasions(items: List<ClothingItemOccasionEntity>)

    /**
     * Updates the pattern associations for a specific clothing item.
     */
    @Transaction
    suspend fun updateItemPatterns(itemId: Long, patternIds: List<Long>) {
        deleteItemPatterns(itemId)
        insertItemPatterns(patternIds.map { ClothingItemPatternEntity(itemId, it) })
    }

    @Query("DELETE FROM clothing_item_patterns WHERE clothing_item_id = :itemId")
    suspend fun deleteItemPatterns(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemPatterns(items: List<ClothingItemPatternEntity>)

    // ── Batch segmentation ───────────────────────────────────────────────────

    /**
     * Returns all items that have an image and have not yet been segmented.
     * Excludes both `.png` (API < 30 segmented output) and `.webp` (API 30+ segmented output)
     * so already-segmented items are not re-queued.
     */
    @Query("SELECT * FROM clothing_items WHERE image_path IS NOT NULL AND image_path NOT LIKE '%.png' AND image_path NOT LIKE '%.webp'")
    suspend fun getItemsNeedingSegmentation(): List<ClothingItemEntity>

    /** Live count of items eligible for batch segmentation. Updates whenever the table changes. */
    @Query("SELECT COUNT(*) FROM clothing_items WHERE image_path IS NOT NULL AND image_path NOT LIKE '%.png' AND image_path NOT LIKE '%.webp'")
    fun getSegmentationEligibleCount(): Flow<Int>

    /** Replaces the stored image path for a single item and updates its timestamp. */
    @Query("UPDATE clothing_items SET image_path = :imagePath, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateItemImagePath(id: Long, imagePath: String, updatedAt: java.time.Instant)

    // ── RAG / semantic pipeline ──────────────────────────────────────────────

    /** One-shot fetch of a fully-loaded [ClothingItemDetail] (used by ItemVectorizer after save). */
    @Transaction
    @Query("SELECT ci.*, $WEAR_COUNT_SUBQUERY FROM clothing_items ci WHERE ci.id = :id")
    suspend fun getClothingItemDetailOnce(id: Long): ClothingItemDetail?

    /** Items that have an image but no caption yet — input queue for batch enrichment. */
    @Query("SELECT * FROM clothing_items WHERE image_path IS NOT NULL AND image_caption IS NULL")
    suspend fun getItemsNeedingCaption(): List<ClothingItemEntity>

    /** Live count of items eligible for batch caption enrichment. */
    @Query("SELECT COUNT(*) FROM clothing_items WHERE image_path IS NOT NULL AND image_caption IS NULL")
    fun getCaptionEligibleCount(): Flow<Int>

    /** Writes the ItemVectorizer output for a single item. */
    @Query("UPDATE clothing_items SET semantic_description = :text, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSemanticDescription(id: Long, text: String, updatedAt: java.time.Instant)

    /** Writes the ML Kit image caption for a single item. Returns the number of rows affected (0 if the item was deleted). */
    @Query("UPDATE clothing_items SET image_caption = :caption, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateImageCaption(id: Long, caption: String, updatedAt: java.time.Instant): Int

    /** Fetches fully-loaded [ClothingItemDetail] for a specific set of IDs (one-shot, for RAG context building). */
    @Transaction
    @Query("SELECT ci.*, $WEAR_COUNT_SUBQUERY FROM clothing_items ci WHERE ci.id IN (:ids)")
    suspend fun getItemDetailsByIds(ids: List<Long>): List<ClothingItemDetail>

    // ── Image compression ────────────────────────────────────────────────────

    /** Returns all non-null image paths; used by [ImageCompressionWorker] to build its work queue. */
    @Query("SELECT image_path FROM clothing_items WHERE image_path IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    // ── Chat router queries ───────────────────────────────────────────────────

    /**
     * Returns the total count of clothing items. Used by [com.closet.features.chat.ChatRouter]
     * for "how many items do I own?" queries.
     */
    @Query("SELECT COUNT(*) FROM clothing_items")
    suspend fun getItemCount(): Int

    /**
     * Returns all items that have never been worn (no entry in [outfit_log_items] at all).
     * Used by [com.closet.features.chat.ChatRouter] for "what have I never worn?" queries.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path
        FROM clothing_items ci
        WHERE NOT EXISTS (
            SELECT 1
            FROM outfit_log_items oli
            WHERE oli.clothing_item_id = ci.id
        )
        ORDER BY ci.name ASC
    """)
    suspend fun getItemsNeverWorn(): List<NotWornItem>

    /**
     * Returns all items currently marked as dirty (wash_status = 'Dirty').
     * Used by [com.closet.features.chat.ChatRouter] for "what's in my laundry?" queries.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path
        FROM clothing_items ci
        WHERE ci.wash_status = 'Dirty'
        ORDER BY ci.name ASC
    """)
    suspend fun getItemsNeedingWash(): List<NotWornItem>

    /**
     * Returns the single most-worn item across all time, or null if nothing has been worn.
     * Used by [com.closet.features.chat.ChatRouter] for "what's my most worn item?" queries.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path, $WEAR_COUNT_SUBQUERY
        FROM clothing_items ci
        ORDER BY wear_count DESC
        LIMIT 1
    """)
    suspend fun getMostWornItem(): WearCountResult?

    /**
     * Returns the single closest item whose name matches [query] (case-insensitive LIKE),
     * along with its wear count. Returns null if no item matches.
     * Used by [com.closet.features.chat.ChatRouter] for wear-count pattern queries.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path, $WEAR_COUNT_SUBQUERY
        FROM clothing_items ci
        WHERE ci.name LIKE '%' || :query || '%'
        ORDER BY length(ci.name) ASC
        LIMIT 1
    """)
    suspend fun getWearCountByName(query: String): WearCountResult?

    /**
     * Returns all items that have not been worn after [cutoffDate] (YYYY-MM-DD, exclusive).
     * An item counts as worn if it appears in [outfit_log_items] linked to a log strictly
     * after [cutoffDate]. Items worn exactly on [cutoffDate] are treated as "not worn in N days"
     * (boundary is exclusive so the label matches — "30 days" includes day-30 items).
     * Items with no wear history at all are also included.
     * Used by [com.closet.features.chat.ChatRouter] for "haven't worn in N days" queries.
     */
    @Query("""
        SELECT ci.id, ci.name, ci.image_path
        FROM clothing_items ci
        WHERE NOT EXISTS (
            SELECT 1
            FROM outfit_logs ol
            JOIN outfit_log_items oli ON oli.outfit_log_id = ol.id
            WHERE oli.clothing_item_id = ci.id
            AND ol.date > :cutoffDate
        )
        ORDER BY ci.name ASC
    """)
    suspend fun getItemsNotWornSince(cutoffDate: String): List<NotWornItem>
}

/** Result of a wear-count lookup by item name — used by the [com.closet.features.chat.ChatRouter]. */
data class WearCountResult(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "wear_count") val wearCount: Int,
)

/** A clothing item with no recent wear — returned by [ClothingDao.getItemsNotWornSince]. */
data class NotWornItem(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
)
