package com.closet.core.data.repository

import com.closet.core.data.dao.LookupDao
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for lookup table data.
 * Parity: This provides the data for PickerSheets and FilterPanels.
 */
@Singleton
class LookupRepository @Inject constructor(
    private val lookupDao: LookupDao
) {
    /**
     * Retrieves all categories for the wardrobe.
     * @return A [Flow] emitting a list of [CategoryEntity].
     */
    fun getCategories(): Flow<List<CategoryEntity>> = lookupDao.getCategories()

    /**
     * Retrieves subcategories for a specific category.
     * @param categoryId The ID of the parent category.
     * @return A [Flow] emitting a list of [SubcategoryEntity].
     */
    fun getSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>> = 
        lookupDao.getSubcategories(categoryId)

    /**
     * Retrieves all seasonal tags (e.g., Summer, Winter).
     * @return A [Flow] emitting a list of [SeasonEntity].
     */
    fun getSeasons(): Flow<List<SeasonEntity>> = lookupDao.getSeasons()

    /**
     * Retrieves all occasion tags (e.g., Formal, Casual).
     * @return A [Flow] emitting a list of [OccasionEntity].
     */
    fun getOccasions(): Flow<List<OccasionEntity>> = lookupDao.getOccasions()

    /**
     * Retrieves all available color tags.
     * @return A [Flow] emitting a list of [ColorEntity].
     */
    fun getColors(): Flow<List<ColorEntity>> = lookupDao.getColors()

    /**
     * Retrieves all available material tags (e.g., Cotton, Wool).
     * @return A [Flow] emitting a list of [MaterialEntity].
     */
    fun getMaterials(): Flow<List<MaterialEntity>> = lookupDao.getMaterials()

    /**
     * Retrieves all available pattern tags (e.g., Solid, Plaid).
     * @return A [Flow] emitting a list of [PatternEntity].
     */
    fun getPatterns(): Flow<List<PatternEntity>> = lookupDao.getPatterns()

    /**
     * Retrieves all size systems (e.g., EU, US, UK).
     * @return A [Flow] emitting a list of [SizeSystemEntity].
     */
    fun getSizeSystems(): Flow<List<SizeSystemEntity>> = lookupDao.getSizeSystems()

    /**
     * Retrieves all available size values for a specific system (e.g., S, M, L).
     * @param systemId The ID of the size system.
     * @return A [Flow] emitting a list of [SizeValueEntity].
     */
    fun getSizeValues(systemId: Long): Flow<List<SizeValueEntity>> = 
        lookupDao.getSizeValues(systemId)

    /**
     * Retrieves a distinct list of brands used in existing wardrobe items for autocomplete.
     * @return A list of unique brand names.
     */
    suspend fun getDistinctBrands(): List<String> = lookupDao.getDistinctBrands()
}
