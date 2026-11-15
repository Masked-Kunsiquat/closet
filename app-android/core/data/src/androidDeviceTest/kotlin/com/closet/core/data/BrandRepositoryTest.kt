package com.closet.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Duplicate brand names must still map to [AppError.DatabaseError.ConstraintViolation] now that
 * the bundled driver throws `androidx.sqlite.SQLiteException` instead of the framework's
 * `SQLiteConstraintException`. Built with [configureClosetDatabase], as production is.
 */
@RunWith(AndroidJUnit4::class)
class BrandRepositoryTest {

    private lateinit var db: ClothingDatabase
    private lateinit var repository: BrandRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<ClothingDatabase>(context)
            .configureClosetDatabase()
            .build()
        repository = BrandRepository(db.brandDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertBrand_duplicateName_isConstraintViolation() = runBlocking {
        assertTrue(repository.insertBrand("Acme") is DataResult.Success)
        val result = repository.insertBrand("ACME")
        assertTrue("expected ConstraintViolation, got $result",
            result is DataResult.Error && result.throwable is AppError.DatabaseError.ConstraintViolation)
    }

    @Test
    fun updateBrand_toExistingName_isConstraintViolation() = runBlocking {
        repository.insertBrand("Acme")
        val other = (repository.insertBrand("Other") as DataResult.Success).data
        val result = repository.updateBrand(other, "acme")
        assertTrue("expected ConstraintViolation, got $result",
            result is DataResult.Error && result.throwable is AppError.DatabaseError.ConstraintViolation)
    }
}
