package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.closet.core.data.model.BrandEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for brand lookup data. All write operations use raw SQL to avoid Room
 * auto-generating a full entity insert for the name-only table.
 */
@Dao
interface BrandDao {

    /** Returns all brands ordered alphabetically as a live [Flow]. */
    @Query("SELECT * FROM brands ORDER BY name")
    fun getAllBrands(): Flow<List<BrandEntity>>

    /** Returns the brand with the given [id], or null if not found. */
    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getBrandById(id: Long): BrandEntity?

    /**
     * Inserts a new brand with the given [name] and its lowercase [normalizedName].
     * @return The row ID of the newly inserted brand.
     * @throws android.database.sqlite.SQLiteConstraintException if a brand with [normalizedName] already exists.
     */
    @Query("INSERT INTO brands (name, normalized_name) VALUES (:name, :normalizedName)")
    suspend fun insertBrand(name: String, normalizedName: String): Long

    /**
     * Updates the brand record (name change).
     * @return The number of rows updated (0 if the brand ID was not found).
     */
    @Update
    suspend fun updateBrand(brand: BrandEntity): Int

    /**
     * Deletes the brand only if no clothing items reference it.
     * @return The number of rows deleted (0 if the brand is still in use, 1 on success).
     */
    @Query("DELETE FROM brands WHERE id = :id AND NOT EXISTS (SELECT 1 FROM clothing_items WHERE brand_id = :id)")
    suspend fun deleteBrandIfUnused(id: Long): Int

    /** Returns the number of clothing items currently assigned to [brandId]. */
    @Query("SELECT COUNT(*) FROM clothing_items WHERE brand_id = :brandId")
    suspend fun getItemCountForBrand(brandId: Long): Int
}
