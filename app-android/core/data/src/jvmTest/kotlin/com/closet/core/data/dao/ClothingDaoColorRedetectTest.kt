package com.closet.core.data.dao

import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bulk colour re-detect queue (#128): items with a photo, optionally only uncurated ones. */
class ClothingDaoColorRedetectTest {
    private lateinit var testDb: TestDatabase
    private val dao: ClothingDao get() = testDb.db.clothingDao()

    private var uncurated = 0L
    private var curated = 0L

    @BeforeTest fun setUp() = runBlocking {
        testDb = TestDatabase()
        uncurated = dao.insertClothingItem(ClothingItemEntity(name = "Tee", imagePath = "a.webp"))
        curated = dao.insertClothingItem(
            ClothingItemEntity(name = "Shirt", imagePath = "b.jpg", colorsCurated = true),
        )
        dao.insertClothingItem(ClothingItemEntity(name = "No photo"))
        dao.insertClothingItem(ClothingItemEntity(name = "No photo, curated", colorsCurated = true))
        Unit
    }

    @AfterTest fun tearDown() = testDb.close()

    @Test fun `only uncurated counts items with a photo whose colours the user has not edited`() = runBlocking {
        assertEquals(1, dao.getColorRedetectEligibleCount(onlyUncurated = true).first())
        assertEquals(listOf(uncurated), dao.getColorRedetectItems(onlyUncurated = true).map { it.id })
    }

    @Test fun `all counts every item with a photo, curated included`() = runBlocking {
        assertEquals(2, dao.getColorRedetectEligibleCount(onlyUncurated = false).first())
        assertEquals(
            setOf(uncurated, curated),
            dao.getColorRedetectItems(onlyUncurated = false).map { it.id }.toSet(),
        )
    }

    @Test fun `the count follows the curated flag`() = runBlocking {
        dao.setColorsCurated(curated, false)
        assertEquals(2, dao.getColorRedetectEligibleCount(onlyUncurated = true).first())
    }
}
