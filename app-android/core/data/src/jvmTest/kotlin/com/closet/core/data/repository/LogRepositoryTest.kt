package com.closet.core.data.repository

import com.closet.core.data.TestDatabase
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class LogRepositoryTest {
    private lateinit var testDb: TestDatabase

    @BeforeTest fun setUp() { testDb = TestDatabase() }
    @AfterTest fun tearDown() { testDb.close() }

    private fun repo(zone: TimeZone) = LogRepository(
        testDb.db.logDao(),
        FixedAppClock(Instant.parse("2026-01-15T07:30:00Z"), zone),
    )

    @Test fun wearOutfitTodayLogsTheClockDate() = runBlocking {
        val outfitId = testDb.db.outfitDao().insertOutfit(OutfitEntity(name = "Test"))
        val result = repo(TimeZone.of("America/Los_Angeles")).wearOutfitToday(outfitId)
        assertTrue(result is DataResult.Success)
        assertEquals(1, testDb.db.logDao().getLogsByDate("2026-01-14").first().size)
        assertTrue(testDb.db.logDao().getLogsByDate("2026-01-15").first().isEmpty())
    }

    @Test fun wearItemsTodayLogsTheClockDate() = runBlocking {
        val result = repo(TimeZone.UTC).wearItemsToday(emptyList())
        assertTrue(result is DataResult.Success)
        assertEquals(1, testDb.db.logDao().getLogsByDate("2026-01-15").first().size)
    }
}
