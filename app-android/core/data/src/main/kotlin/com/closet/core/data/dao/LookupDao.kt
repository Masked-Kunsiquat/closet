package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.closet.core.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Mirror of Lookup table queries from queries.ts.
 */
@Dao
interface LookupDao {

    @Query("SELECT * FROM categories ORDER BY sort_order")
    fun getCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM subcategories WHERE category_id = :categoryId ORDER BY sort_order")
    fun getSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM seasons ORDER BY id")
    fun getSeasons(): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM occasions ORDER BY id")
    fun getOccasions(): Flow<List<OccasionEntity>>

    @Query("SELECT * FROM colors ORDER BY name")
    fun getColors(): Flow<List<ColorEntity>>

    @Query("SELECT * FROM materials ORDER BY name")
    fun getMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM patterns ORDER BY name")
    fun getPatterns(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM size_systems ORDER BY id")
    fun getSizeSystems(): Flow<List<SizeSystemEntity>>

    @Query("SELECT * FROM size_values WHERE size_system_id = :systemId ORDER BY sort_order")
    fun getSizeValues(systemId: Long): Flow<List<SizeValueEntity>>

    @Query("SELECT DISTINCT brand FROM clothing_items WHERE brand IS NOT NULL AND brand != '' ORDER BY brand")
    suspend fun getDistinctBrands(): List<String>
}
