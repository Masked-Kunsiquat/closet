package com.closet.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [OutfitRepository] error handling against a real in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class OutfitRepositoryTest {

    private lateinit var db: ClothingDatabase
    private lateinit var repository: OutfitRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ClothingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OutfitRepository(db, db.outfitDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Updating an outfit that doesn't exist throws [AppError.DatabaseError.NotFound] inside the
     * transaction. The repository must return it as a [DataResult.Error], never let it escape:
     * [AppError] extends Throwable, not Exception, so a `catch (e: Exception)` alone misses it.
     */
    @Test
    fun updateOutfit_missingOutfit_returnsNotFoundError() = runBlocking {
        val result = repository.updateOutfit(
            outfitId = 999L,
            name = "Ghost",
            notes = null,
            items = emptyList(),
        )

        assertTrue("expected DataResult.Error, got $result", result is DataResult.Error)
        val throwable = (result as DataResult.Error).throwable
        assertTrue(
            "expected NotFound, got $throwable",
            throwable is AppError.DatabaseError.NotFound,
        )
    }
}
