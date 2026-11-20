package com.closet.core.data.repository

import com.closet.core.data.DatabaseSeeder
import com.closet.core.data.TestDatabase
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.ClothingItemColorEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.TaggedColor
import com.closet.core.data.time.FixedAppClock
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class ClothingRepositoryColorsTest {
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

    private fun colorId(name: String): Long = runBlocking {
        testDb.db.lookupDao().getAllColors().first { it.name == name }.id
    }

    private fun newItem(): Long = runBlocking {
        testDb.db.clothingDao().insertClothingItem(ClothingItemEntity(name = "Tee"))
    }

    private fun rows(itemId: Long): Set<ClothingItemColorEntity> = runBlocking {
        assertIs<DataResult.Success<List<ClothingItemColorEntity>>>(repo.getItemColorRows(itemId)).data.toSet()
    }

    private fun TaggedColor.row(itemId: Long) = ClothingItemColorEntity(itemId, colorId, hex, share, source)

    @Test
    fun `tagged colours round trip`() = runBlocking {
        val item = newItem()
        val colors = listOf(
            TaggedColor(colorId("Navy"), hex = "#1F2A44", share = 0.7, source = ColorSource.AUTO),
            TaggedColor(colorId("White")),
        )
        assertIs<DataResult.Success<Unit>>(repo.updateItemColors(item, colors))
        assertEquals(colors.map { it.row(item) }.toSet(), rows(item))
        assertEquals(ColorSource.USER, rows(item).single { it.colorId == colorId("White") }.source)
    }

    @Test
    fun `replaceAutoColors skips user curated items`() = runBlocking {
        val item = newItem()
        val user = TaggedColor(colorId("Red"))
        repo.updateItemColors(item, listOf(user))

        val result = repo.replaceAutoColors(item, listOf(ExtractedColor(colorId("Navy"), "#1F2A44", 1.0)))

        assertEquals(false, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(setOf(user.row(item)), rows(item))
    }

    @Test
    fun `replaceAutoColors replaces auto rows`() = runBlocking {
        val item = newItem()
        repo.updateItemColors(item, listOf(TaggedColor(colorId("Red"), "#D62B2B", 1.0, ColorSource.AUTO)))
        val extracted = listOf(
            ExtractedColor(colorId("Navy"), "#1F2A44", 0.6),
            ExtractedColor(colorId("White"), "#F4F4F2", 0.4),
        )

        val result = repo.replaceAutoColors(item, extracted)

        assertEquals(true, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(
            extracted.map { ClothingItemColorEntity(item, it.colorId, it.hex, it.share, ColorSource.AUTO) }.toSet(),
            rows(item),
        )
    }

    @Test
    fun `replaceAutoColors leaves auto rows unchanged on an empty extraction`() = runBlocking {
        val item = newItem()
        val auto = TaggedColor(colorId("Red"), "#D62B2B", 1.0, ColorSource.AUTO)
        repo.updateItemColors(item, listOf(auto))

        val result = repo.replaceAutoColors(item, emptyList())

        assertEquals(false, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(setOf(auto.row(item)), rows(item))
    }

    @Test
    fun `replaceAutoColors fills an untagged item`() = runBlocking {
        val item = newItem()
        val extracted = listOf(ExtractedColor(colorId("Olive"), "#6B6B3A", 1.0))

        val result = repo.replaceAutoColors(item, extracted)

        assertEquals(true, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(setOf(ClothingItemColorEntity(item, colorId("Olive"), "#6B6B3A", 1.0, ColorSource.AUTO)), rows(item))
    }

    @Test
    fun `updateColorsAsUser with the same set writes nothing`() = runBlocking {
        val item = newItem()
        val auto = listOf(
            TaggedColor(colorId("Navy"), "#1F2A44", 0.6, ColorSource.AUTO),
            TaggedColor(colorId("White"), "#F4F4F2", 0.4, ColorSource.AUTO),
        )
        repo.updateItemColors(item, auto)

        // Same set, different order and a duplicate: still unchanged.
        val result = repo.updateColorsAsUser(item, listOf(colorId("White"), colorId("Navy"), colorId("White")))

        assertEquals(false, assertIs<DataResult.Success<Boolean>>(result).data)
        // Still AUTO: a write would have turned them into USER rows.
        assertEquals(auto.map { it.row(item) }.toSet(), rows(item))
    }

    @Test
    fun `updateColorsAsUser with a changed set writes user rows keeping retained measurements`() = runBlocking {
        val item = newItem()
        repo.updateItemColors(
            item,
            listOf(
                TaggedColor(colorId("Navy"), "#1F2A44", 0.6, ColorSource.AUTO),
                TaggedColor(colorId("White"), "#F4F4F2", 0.4, ColorSource.AUTO),
            ),
        )

        val result = repo.updateColorsAsUser(item, listOf(colorId("Navy"), colorId("Red")))

        assertEquals(true, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(
            setOf(
                ClothingItemColorEntity(item, colorId("Navy"), "#1F2A44", 0.6, ColorSource.USER),
                ClothingItemColorEntity(item, colorId("Red"), null, null, ColorSource.USER),
            ),
            rows(item),
        )
    }

    @Test
    fun `updateColorsAsUser can clear every colour`() = runBlocking {
        val item = newItem()
        repo.updateItemColors(item, listOf(TaggedColor(colorId("Navy"), "#1F2A44", 1.0, ColorSource.AUTO)))

        val result = repo.updateColorsAsUser(item, emptyList())

        assertEquals(true, assertIs<DataResult.Success<Boolean>>(result).data)
        assertEquals(emptySet(), rows(item))
    }

    @Test
    fun `getReferenceShades lists every seeded shade`() = runBlocking {
        val shades = assertIs<DataResult.Success<List<ReferenceShade>>>(
            LookupRepository(testDb.db.lookupDao()).getReferenceShades()
        ).data
        assertEquals(83, shades.size)
        val colorIds = testDb.db.lookupDao().getAllColors().map { it.id }.toSet()
        assertEquals(emptyList<ReferenceShade>(), shades.filter { it.colorId !in colorIds })
        // Seed ids follow SEEDED_COLORS order (Black = 1 … Teal = 22), which the commonTest
        // SeededShades lookup relies on.
        val expected = DatabaseSeeder.SEEDED_COLORS.flatMapIndexed { i, c ->
            c.shades.map { ReferenceShade((i + 1).toLong(), it) }
        }
        assertEquals(expected.toSet(), shades.toSet())
    }
}
