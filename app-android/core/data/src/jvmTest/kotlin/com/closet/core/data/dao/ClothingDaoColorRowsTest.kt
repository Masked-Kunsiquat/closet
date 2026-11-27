package com.closet.core.data.dao

import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.TaggedColor
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The batched colour-row read used by the chat tools (item results carry each colour's share). */
class ClothingDaoColorRowsTest {
    private lateinit var testDb: TestDatabase
    private val dao: ClothingDao get() = testDb.db.clothingDao()

    @BeforeTest fun setUp() { testDb = TestDatabase() }

    @AfterTest fun tearDown() = testDb.close()

    @Test fun `getItemColorRowsForItems returns rows for every requested item by share`() = runBlocking {
        val a = dao.insertClothingItem(ClothingItemEntity(name = "A"))
        val b = dao.insertClothingItem(ClothingItemEntity(name = "B"))
        val other = dao.insertClothingItem(ClothingItemEntity(name = "Other"))
        dao.updateItemColors(a, listOf(
            TaggedColor(1, "#111111", 0.3, ColorSource.AUTO), TaggedColor(2, "#222222", 0.7, ColorSource.AUTO),
        ))
        dao.updateItemColors(b, listOf(
            TaggedColor(3, "#333333", 0.2, ColorSource.AUTO), TaggedColor(4, "#444444", 0.8, ColorSource.AUTO),
        ))
        dao.updateItemColors(other, listOf(TaggedColor(5, "#555555", 1.0, ColorSource.AUTO)))

        val rows = dao.getItemColorRowsForItems(listOf(b, a))

        assertEquals(listOf(a to 2L, a to 1L, b to 4L, b to 3L), rows.map { it.clothingItemId to it.colorId })
    }

    @Test fun `a row without a share sorts after the rows that have one`() = runBlocking {
        val a = dao.insertClothingItem(ClothingItemEntity(name = "A"))
        // Lowest colour id and no share: only the NULL ordering can put it last.
        dao.updateItemColors(a, listOf(
            TaggedColor(1, "#111111", null, ColorSource.USER),
            TaggedColor(2, "#222222", 0.1, ColorSource.AUTO),
            TaggedColor(3, "#333333", 0.9, ColorSource.AUTO),
        ))

        val rows = dao.getItemColorRowsForItems(listOf(a))

        assertEquals(listOf(3L, 2L, 1L), rows.map { it.colorId })
    }
}
