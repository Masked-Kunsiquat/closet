package com.closet.core.data.repository

import com.closet.core.data.dao.BrandDao
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for brand CRUD operations.
 *
 * All suspend operations wrap results in [DataResult] and re-throw [kotlinx.coroutines.CancellationException]
 * so callers never need to handle raw exceptions.
 */
@Singleton
class BrandRepository @Inject constructor(
    private val brandDao: BrandDao
) {

    /** Returns all brands ordered alphabetically as a live [DataResult] [Flow]. */
    fun getAllBrands(): Flow<DataResult<List<BrandEntity>>> = brandDao.getAllBrands()
        .map<List<BrandEntity>, DataResult<List<BrandEntity>>> { DataResult.Success(it) }
        .onStart { emit(DataResult.Loading) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            Timber.e(throwable, "Error loading brands")
            emit(DataResult.Error(AppError.DatabaseError.QueryError(throwable)))
        }

    /**
     * Inserts a brand with the given [name].
     * @return [DataResult.Success] containing the new brand's row ID, or
     *   [DataResult.Error] with [com.closet.core.data.util.AppError.DatabaseError.ConstraintViolation]
     *   if the name already exists.
     */
    suspend fun insertBrand(name: String): DataResult<Long> = try {
        val id = brandDao.insertBrand(name, name.lowercase())
        DataResult.Success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteConstraintException) {
        Timber.e(e, "Constraint violation inserting brand: $name")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation(e.message ?: "A brand with that name already exists."))
    } catch (e: Exception) {
        Timber.e(e, "Error inserting brand: $name")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Updates the name of the brand identified by [id].
     * @return [DataResult.Success] on success, or [DataResult.Error] on failure.
     */
    suspend fun updateBrand(id: Long, name: String): DataResult<Unit> = try {
        val updated = brandDao.updateBrand(BrandEntity(id = id, name = name, normalizedName = name.lowercase()))
        if (updated > 0) DataResult.Success(Unit)
        else DataResult.Error(AppError.DatabaseError.NotFound())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteConstraintException) {
        Timber.e(e, "Constraint violation updating brand id=$id")
        DataResult.Error(AppError.DatabaseError.ConstraintViolation(e.message ?: "A brand with that name already exists."))
    } catch (e: Exception) {
        Timber.e(e, "Error updating brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /**
     * Deletes the brand only if it has no associated clothing items.
     * @return [DataResult.Error] with [com.closet.core.data.util.AppError.ValidationError.InvalidInput]
     *   if the brand is still in use, otherwise [DataResult.Success].
     */
    suspend fun deleteBrand(id: Long): DataResult<Unit> = try {
        brandDao.getBrandById(id)
            ?: return DataResult.Error(AppError.DatabaseError.NotFound())
        val deleted = brandDao.deleteBrandIfUnused(id)
        if (deleted == 0) {
            DataResult.Error(AppError.ValidationError.InvalidInput("Brand is still assigned to one or more items and cannot be deleted."))
        } else {
            DataResult.Success(Unit)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error deleting brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }

    /** Returns the number of clothing items currently assigned to the brand with [id]. */
    suspend fun getItemCountForBrand(id: Long): DataResult<Int> = try {
        DataResult.Success(brandDao.getItemCountForBrand(id))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Error fetching item count for brand id=$id")
        DataResult.Error(AppError.DatabaseError.QueryError(e))
    }
}
