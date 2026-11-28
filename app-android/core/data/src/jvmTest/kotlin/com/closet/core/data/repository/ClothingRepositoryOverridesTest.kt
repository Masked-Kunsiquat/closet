package com.closet.core.data.repository

import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** The item warmth and rain-ready overrides (#147) through the repository save path and the candidate query. */
class ClothingRepositoryOverridesTest {
    private lateinit var testDb: TestDatabase
    private lateinit var repo: ClothingRepository

    @BeforeTest fun setUp() {
        testDb = TestDatabase()
        repo = ClothingRepository(
            testDb.db,
            testDb.db.clothingDao(),
            FixedAppClock(Instant.parse("2026-01-15T07:30:00Z"), TimeZone.UTC),
        )
    }

    @AfterTest fun tearDown() { testDb.close() }

    private fun allSeasonId(): Long = runBlocking {
        testDb.db.lookupDao().getSeasons().first().first { it.name == "All Season" }.id
    }

    private fun load(id: Long): ClothingItemEntity = runBlocking {
        assertIs<DataResult.Success<ClothingItemEntity>>(repo.getItemEntityById(id)).data
    }

    @Test
    fun `item overrides round trip`() = runBlocking {
        val insert = repo.insertItem(ClothingItemEntity(name = "Wool coat", warmthOverride = 3, rainReadyOverride = false))
        val id = assertIs<DataResult.Success<Long>>(insert).data

        val saved = load(id)
        assertEquals(3, saved.warmthOverride)
        assertEquals(false, saved.rainReadyOverride)

        assertIs<DataResult.Success<Int>>(repo.updateItem(saved.copy(warmthOverride = 0, rainReadyOverride = true)))
        val updated = load(id)
        assertEquals(0, updated.warmthOverride)
        assertEquals(true, updated.rainReadyOverride)

        assertIs<DataResult.Success<Int>>(repo.updateItem(updated.copy(warmthOverride = null, rainReadyOverride = null)))
        val cleared = load(id)
        assertNull(cleared.warmthOverride)
        assertNull(cleared.rainReadyOverride)
    }

    @Test
    fun `candidates carry overrides`() = runBlocking {
        val seasons = listOf(allSeasonId())
        val coat = assertIs<DataResult.Success<Long>>(
            repo.insertItemWithAttributes(
                ClothingItemEntity(name = "Wool coat", warmthOverride = 4, rainReadyOverride = true),
                colors = emptyList(), seasonIds = seasons, occasionIds = emptyList(),
                materialIds = emptyList(), patternIds = emptyList(),
            )
        ).data
        val tee = assertIs<DataResult.Success<Long>>(
            repo.insertItemWithAttributes(
                ClothingItemEntity(name = "Tee"),
                colors = emptyList(), seasonIds = seasons, occasionIds = emptyList(),
                materialIds = emptyList(), patternIds = emptyList(),
            )
        ).data

        val candidates = testDb.db.recommendationDao()
            .getCandidates(calendarSeason = "Winter", tempLow = null, tempHigh = null, occasionId = null)
            .associateBy { it.id }
        assertEquals(4, candidates.getValue(coat).warmthOverride)
        assertEquals(true, candidates.getValue(coat).rainReadyOverride)
        assertNull(candidates.getValue(tee).warmthOverride)
        assertNull(candidates.getValue(tee).rainReadyOverride)
    }
}
