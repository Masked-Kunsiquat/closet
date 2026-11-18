package com.closet.core.data

import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.time.SystemAppClock
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [OutfitRepository] error handling against a real database built with the production builder
 * ([TestDatabase]), so `useWriterConnection { immediateTransaction { } }` runs on the bundled driver.
 */
class OutfitRepositoryTest {

    private lateinit var testDb: TestDatabase
    private lateinit var repository: OutfitRepository

    @BeforeTest
    fun setUp() {
        testDb = TestDatabase()
        repository = OutfitRepository(testDb.db, testDb.db.outfitDao(), SystemAppClock)
    }

    @AfterTest
    fun tearDown() {
        testDb.close()
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

        assertTrue(result is DataResult.Error, "expected DataResult.Error, got $result")
        val throwable = result.throwable
        assertTrue(
            throwable is AppError.DatabaseError.NotFound,
            "expected NotFound, got $throwable",
        )
    }
}
