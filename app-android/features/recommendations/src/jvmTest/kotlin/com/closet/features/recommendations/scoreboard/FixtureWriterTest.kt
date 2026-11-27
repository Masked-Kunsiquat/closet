package com.closet.features.recommendations.scoreboard

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
}
