package com.closet.core.data.repository

import com.closet.core.data.dao.BrandDao
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandRepository @Inject constructor(
    private val brandDao: BrandDao
) {

    fun getAllBrands(): Flow<List<BrandEntity>> = brandDao.getAllBrands()

    suspend fun insertBrand(name: String): DataResult<Long> = try {
        val id = brandDao.insertBrand(name)
        DataResult.Success(id)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error inserting brand: $name")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun updateBrand(id: Long, name: String): DataResult<Unit> = try {
        brandDao.updateBrand(BrandEntity(id = id, name = name))
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error updating brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun deleteBrand(id: Long): DataResult<Unit> = try {
        brandDao.deleteBrand(id)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error deleting brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    suspend fun getItemCountForBrand(id: Long): DataResult<Int> = try {
        DataResult.Success(brandDao.getItemCountForBrand(id))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error fetching item count for brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
