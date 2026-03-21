package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.closet.core.data.model.BrandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {

    @Query("SELECT * FROM brands ORDER BY name")
    fun getAllBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getBrandById(id: Long): BrandEntity?

    @Query("INSERT INTO brands (name) VALUES (:name)")
    suspend fun insertBrand(name: String): Long

    @Update
    suspend fun updateBrand(brand: BrandEntity)

    @Query("DELETE FROM brands WHERE id = :id AND NOT EXISTS (SELECT 1 FROM clothing_items WHERE brand_id = :id)")
    suspend fun deleteBrandIfUnused(id: Long): Int

    @Query("SELECT COUNT(*) FROM clothing_items WHERE brand_id = :brandId")
    suspend fun getItemCountForBrand(brandId: Long): Int
}
