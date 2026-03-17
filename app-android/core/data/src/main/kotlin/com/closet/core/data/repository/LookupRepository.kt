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
    fun getCategories(): Flow<List<CategoryEntity>> = lookupDao.getCategories()

    fun getSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>> = 
        lookupDao.getSubcategories(categoryId)

    fun getSeasons(): Flow<List<SeasonEntity>> = lookupDao.getSeasons()

    fun getOccasions(): Flow<List<OccasionEntity>> = lookupDao.getOccasions()

    fun getColors(): Flow<List<ColorEntity>> = lookupDao.getColors()

    fun getMaterials(): Flow<List<MaterialEntity>> = lookupDao.getMaterials()

    fun getPatterns(): Flow<List<PatternEntity>> = lookupDao.getPatterns()

    fun getSizeSystems(): Flow<List<SizeSystemEntity>> = lookupDao.getSizeSystems()

    fun getSizeValues(systemId: Long): Flow<List<SizeValueEntity>> = 
        lookupDao.getSizeValues(systemId)

    /**
     * Get distinct brands for autocomplete suggestions.
     */
    suspend fun getDistinctBrands(): List<String> = lookupDao.getDistinctBrands()
}
