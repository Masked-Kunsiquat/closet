package com.closet.features.recommendations.ai

import com.closet.core.data.ai.OutfitSelection
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.engine.OutfitCombo
import com.closet.features.recommendations.engine.engineInput
import com.closet.features.recommendations.engine.item
import com.closet.features.recommendations.engine.key
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The scorer returns at most one combo per core (Top + Bottom, or OnePiece) where the pool allows. */
class OutfitCoherenceScorerCoreTest {

    private val tops = (1L..3L).map { item(id = it, role = "Top") }
    private val bottom = item(id = 10, role = "Bottom")
    private val shoes = (20L..21L).map { item(id = it, role = "Footwear") }

    // Pool in engine order: indices 0-2 share core {1,10} (no shoes, shoe 20, shoe 21);
    // index 3 has core {2,10}; index 4 has core {3,10}.
    private val pool = listOf(
        OutfitCombo(listOf(tops[0], bottom), 1.0),
        OutfitCombo(listOf(tops[0], bottom, shoes[0]), 1.0),
        OutfitCombo(listOf(tops[0], bottom, shoes[1]), 1.0),
        OutfitCombo(listOf(tops[1], bottom), 1.0),
        OutfitCombo(listOf(tops[2], bottom), 1.0),
    )
    private val input = engineInput(candidates = tops + bottom + shoes)

    private fun scorer(selectedIds: List<Int>): OutfitCoherenceScorer {
        val openAi = mockk<OpenAiProvider> {
            coEvery { selectOutfits(any(), any()) } returns
                Result.success(selectedIds.map { OutfitSelection(comboId = it, reason = "pick $it") })
        }
        val repo = mockk<RecommendationRepository> {
            coEvery { getAiContextHints(any()) } returns DataResult.Success(emptyMap())
        }
        return OutfitCoherenceScorer(
            nanoProvider = mockk(relaxed = true),
            openAiProvider = openAi,
            anthropicProvider = mockk(relaxed = true),
            aiPreferencesRepository = mockk(relaxed = true) {
                every { getAiEnabled() } returns flowOf(true)
                every { getSelectedProvider() } returns flowOf(AiProvider.OpenAi)
                every { getOpenAiApiKey() } returns flowOf("test-key")
            },
            recommendationRepository = repo,
        )
    }

    @Test
    fun `distinct AI picks are kept in the AI order`() = runTest {
        val result = assertNotNull(scorer(listOf(4, 1, 3)).score(pool, input, emptyMap()))
        assertEquals(listOf(pool[4], pool[1], pool[3]).map { it.key() }, result.map { it.key() })
        assertEquals(listOf(true, true, true), result.map { it.isAiSelected })
    }

    @Test
    fun `a repeated core is replaced by the next new core from the pool`() = runTest {
        // 1, 0 and 2 all share core {1,10}: keep the first, fill with the pool's new cores.
        val result = assertNotNull(scorer(listOf(1, 0, 2)).score(pool, input, emptyMap()))
        assertEquals(listOf(pool[1], pool[3], pool[4]).map { it.key() }, result.map { it.key() })
        assertEquals(listOf(true, false, false), result.map { it.isAiSelected })
        assertEquals("pick 1", result[0].reason)
    }

    @Test
    fun `with fewer than three cores in the pool the AI repeats fill the rest`() = runTest {
        val twoCores = pool.take(4) // cores {1,10} and {2,10}
        val result = assertNotNull(scorer(listOf(0, 1, 2)).score(twoCores, input, emptyMap()))
        assertEquals(listOf(pool[0], pool[3], pool[1]).map { it.key() }, result.map { it.key() })
    }
}
