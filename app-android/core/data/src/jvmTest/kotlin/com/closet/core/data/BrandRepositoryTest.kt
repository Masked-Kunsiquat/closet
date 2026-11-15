package com.closet.core.data

import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Duplicate brand names must map to [AppError.DatabaseError.ConstraintViolation] now that the
 * bundled driver throws `androidx.sqlite.SQLiteException` instead of the framework's
 * `SQLiteConstraintException`. Runs against the production builder ([TestDatabase]).
 */
class BrandRepositoryTest {

    private lateinit var testDb: TestDatabase
    private lateinit var repository: BrandRepository

    @BeforeTest
    fun setUp() {
        testDb = TestDatabase()
        repository = BrandRepository(testDb.db.brandDao())
    }

    @AfterTest
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun duplicateNameIsConstraintViolation() = runBlocking {
        assertTrue(repository.insertBrand("Nike") is DataResult.Success)
        val result = repository.insertBrand("Nike")
        assertTrue(
            result is DataResult.Error && result.throwable is AppError.DatabaseError.ConstraintViolation,
            "expected ConstraintViolation, got $result",
        )
    }

    @Test
    fun insertBrand_duplicateNameDifferentCase_isConstraintViolation() = runBlocking {
        assertTrue(repository.insertBrand("Acme") is DataResult.Success)
        val result = repository.insertBrand("ACME")
        assertTrue(
            result is DataResult.Error && result.throwable is AppError.DatabaseError.ConstraintViolation,
            "expected ConstraintViolation, got $result",
        )
    }

    @Test
    fun updateBrand_toExistingName_isConstraintViolation() = runBlocking {
        repository.insertBrand("Acme")
        val other = (repository.insertBrand("Other") as DataResult.Success).data
        val result = repository.updateBrand(other, "acme")
        assertTrue(
            result is DataResult.Error && result.throwable is AppError.DatabaseError.ConstraintViolation,
            "expected ConstraintViolation, got $result",
        )
    }
}
