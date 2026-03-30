package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for lookup tables (categories, seasons, colors, etc.).
 * Parity: This is the native equivalent of Lookup table queries from queries.ts.
 */
@Dao
interface LookupDao {

    /**
     * Retrieves all categories ordered by their sort order.
     * @return A [Flow] emitting a list of [CategoryEntity].
     */
    @Query("SELECT * FROM categories ORDER BY sort_order")
    fun getCategories(): Flow<List<CategoryEntity>>

    /**
     * Retrieves subcategories for a specific category, ordered by sort order.
     * @param categoryId The ID of the parent category.
     * @return A [Flow] emitting a list of [SubcategoryEntity].
     */
    @Query("SELECT * FROM subcategories WHERE category_id = :categoryId ORDER BY sort_order")
    fun getSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>>

    /**
     * Retrieves all seasons ordered by ID.
     * @return A [Flow] emitting a list of [SeasonEntity].
     */
    @Query("SELECT * FROM seasons ORDER BY id")
    fun getSeasons(): Flow<List<SeasonEntity>>

    /**
     * Retrieves all occasions ordered by ID.
     * @return A [Flow] emitting a list of [OccasionEntity].
     */
    @Query("SELECT * FROM occasions ORDER BY id")
    fun getOccasions(): Flow<List<OccasionEntity>>

    /**
     * Retrieves all colors ordered alphabetically by name.
     * @return A [Flow] emitting a list of [ColorEntity].
     */
    @Query("SELECT * FROM colors ORDER BY name")
    fun getColors(): Flow<List<ColorEntity>>

    /**
     * One-shot fetch of all colors. Intended for background workers that cannot
     * collect a [Flow] (e.g. [com.closet.features.wardrobe.BatchSegmentationWorker]).
     */
    @Query("SELECT * FROM colors ORDER BY name")
    suspend fun getAllColors(): List<ColorEntity>

    /**
     * Retrieves all materials ordered alphabetically by name.
     * @return A [Flow] emitting a list of [MaterialEntity].
     */
    @Query("SELECT * FROM materials ORDER BY name")
    fun getMaterials(): Flow<List<MaterialEntity>>

    /**
     * Retrieves all patterns ordered alphabetically by name.
     * @return A [Flow] emitting a list of [PatternEntity].
     */
    @Query("SELECT * FROM patterns ORDER BY name")
    fun getPatterns(): Flow<List<PatternEntity>>

    /**
     * Retrieves all size systems ordered by ID.
     * @return A [Flow] emitting a list of [SizeSystemEntity].
     */
    @Query("SELECT * FROM size_systems ORDER BY id")
    fun getSizeSystems(): Flow<List<SizeSystemEntity>>

    /**
     * Retrieves size values for a specific size system, ordered by sort order.
     * @param systemId The ID of the size system.
     * @return A [Flow] emitting a list of [SizeValueEntity].
     */
    @Query("SELECT * FROM size_values WHERE size_system_id = :systemId ORDER BY sort_order")
    fun getSizeValues(systemId: Long): Flow<List<SizeValueEntity>>

}
