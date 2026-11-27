package com.closet.features.recommendations.scoreboard

import com.closet.core.data.color.ColorNamer
import com.closet.core.data.color.Oklab
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixtureWriterTest {

    private suspend fun candidateIds(env: ScoreboardDatabase): Set<Long> {
        val workId = env.db.lookupDao().getOccasions().first().first { it.name == "Work/Business" }.id
        val result = RecommendationRepository(env.db.recommendationDao())
            .getCandidates("Winter", null, null, workId)
        return (result as DataResult.Success).data.map { it.id }.toSet()
    }

    @Test
    fun `written items are returned as candidates for their season and occasion`() = runTest {
        ScoreboardDatabase().use { env ->
            val persona = MiniPersona.build()
            val written = FixtureWriter.write(env.db, persona)
            val expected = persona.items
                .filter { ("Winter" in it.seasons || "All Season" in it.seasons) && "Work/Business" in it.occasions }
                .map { written.itemIds.getValue(it.key) }
                .toSet()
            assertTrue(expected.isNotEmpty())
            assertEquals(expected, candidateIds(env))
        }
    }

    @Test
    fun `a dirty item is not a candidate`() = runTest {
        ScoreboardDatabase().use { env ->
            val written = FixtureWriter.write(env.db, MiniPersona.build())
            val id = written.itemIds.getValue("white-oxford")
            assertTrue(id in candidateIds(env))
            env.db.clothingDao().updateWashStatus(id, WashStatus.Dirty.label, "2026-01-01T00:00:00Z")
            assertTrue(id !in candidateIds(env))
        }
    }

    @Test
    fun `unknown lookup names fail loudly`() = runTest {
        ScoreboardDatabase().use { env ->
            val base = MiniPersona.build()
            val bad = base.items.first().copy(category = "Hats Of Doom")
            val error = assertFailsWith<IllegalArgumentException> {
                FixtureWriter.write(env.db, base.copy(items = listOf(bad)))
            }
            assertTrue("Hats Of Doom" in error.message.orEmpty())
        }
    }

    @Test
    fun `saved outfits are written with their items`() = runTest {
        ScoreboardDatabase().use { env ->
            val persona = MiniPersona.build()
            val written = FixtureWriter.write(env.db, persona)
            val saved = persona.outfits.filter { it.saved }
            assertEquals(saved.map { it.key }.toSet(), written.savedOutfitIds.keys)
            for (outfit in saved) {
                val stored = env.db.outfitDao().getOutfitWithItems(written.savedOutfitIds.getValue(outfit.key)).first()
                val ids = stored!!.items.map { it.outfitItem.clothingItemId }.toSet()
                assertEquals(outfit.itemKeys.map { written.itemIds.getValue(it) }.toSet(), ids)
            }
        }
    }

    @Test
    fun `colours are named by the app colour namer`() = runTest {
        ScoreboardDatabase().use { env ->
            val persona = MiniPersona.build()
            val written = FixtureWriter.write(env.db, persona)
            val namer = ColorNamer(env.db.lookupDao().getAllColorShades().map { ReferenceShade(it.colorId, it.hex) })
            for (item in persona.items) {
                val rows = env.db.clothingDao().getItemColorRows(written.itemIds.getValue(item.key))
                val expected = item.colors.map { (hex, _) -> namer.nearest(Oklab.fromHex(hex)) }
                assertEquals(expected, rows.map { it.colorId }, item.key)
            }
        }
    }

    // Two swatches the app names the same colour become one tag, as ColorExtractor does: shares
    // add, and the hex is the share-weighted mean in linear RGB.
    @Test
    fun `swatches named the same colour merge into one row`() = runTest {
        ScoreboardDatabase().use { env ->
            val base = MiniPersona.build()
            val namer = ColorNamer(env.db.lookupDao().getAllColorShades().map { ReferenceShade(it.colorId, it.hex) })
            val dark = "#1F2A44"
            val near = "#222D47"
            assertEquals(namer.nearest(Oklab.fromHex(dark)), namer.nearest(Oklab.fromHex(near)))
            val item = base.items.first().copy(colors = listOf(dark to 0.5, near to 0.25))
            val written = FixtureWriter.write(env.db, base.copy(items = listOf(item) + base.items.drop(1)))

            val rows = env.db.clothingDao().getItemColorRows(written.itemIds.getValue(item.key))
            assertEquals(1, rows.size)
            assertEquals(namer.nearest(Oklab.fromHex(dark)), rows.single().colorId)
            assertEquals(0.75, rows.single().share!!, 1e-9)
            val lin = { hex: String, i: Int -> Oklab.srgbToLinear(hex.substring(1 + 2 * i, 3 + 2 * i).toInt(16) / 255.0) }
            val mean = DoubleArray(3) { i -> (0.5 * lin(dark, i) + 0.25 * lin(near, i)) / 0.75 }
            assertEquals(Oklab.hexFromLinear(mean[0], mean[1], mean[2]), rows.single().hex)
        }
    }
}
