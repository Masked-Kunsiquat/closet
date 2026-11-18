package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Engine v2 (#96) assembly: the per-core beam, exclusion inside the search, layering fairness. */
class EngineBeamTest {

    private val engine = OutfitRecommendationEngine()
    private val today = LocalDate(2026, 3, 31)

    // =========================================================================
    // Per-core beam
    // =========================================================================

    /**
     * Five tops x one bottom, four shoes. Tops 2-5 were worn yesterday (x 0.90), top 1 never, so
     * every shoe outfit of core 1 + 6 outscores every outfit of the other cores.
     */
    private val dominantCoreInput = engineInput(
        candidates = (1L..5L).map { item(id = it, role = "Top") } +
            item(id = 6, role = "Bottom") +
            (7L..10L).map { item(id = it, role = "Footwear") },
        lastWornDates = (2L..5L).associateWith { ItemLastWorn(it, "2026-03-30") },
        today = today,
    )

    @Test
    fun `one dominant core does not crowd the others out of the page`() {
        val result = engine.recommend(dominantCoreInput, topN = 3)
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.core() }.toSet().size, "cores: ${result.map { it.core() }}")
        assertEquals(setOf(1L, 6L), result.first().core())
    }

    @Test
    fun `one dominant core does not crowd the others out of a seeded page`() {
        repeat(20) { seed ->
            val result = engine.recommend(dominantCoreInput, topN = 5, jitter = Random(seed))
            assertEquals(5, result.map { it.core() }.toSet().size, "seed $seed: ${result.map { it.core() }}")
        }
    }

    // =========================================================================
    // Exclusion inside the search
    // =========================================================================

    /** Two tops x one bottom, six shoes: 7 complete outfits per core, more than one beam holds. */
    private val manyShoesInput = engineInput(
        candidates = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Top"), item(id = 3, role = "Bottom")) +
            (4L..9L).map { item(id = it, role = "Footwear") },
    )

    @Test
    fun `excluding every core's whole beam still fills a page at the base beam size`() {
        val base = { exclude: Set<Set<Long>>, topN: Int ->
            engine.assemble(
                manyShoesInput, topN = topN, exclude = exclude, random = null,
                coreBeam = OutfitRecommendationEngine.CORE_BEAM,
                beamPerCore = OutfitRecommendationEngine.BEAM_PER_CORE,
            )
        }
        val firstBeams = base(emptySet(), 100)
        assertEquals(2 * OutfitRecommendationEngine.BEAM_PER_CORE, firstBeams.size, "each core fills its beam")
        val shown = firstBeams.map { it.key() }.toSet()

        // No widening: the base beam sizes alone must find unseen outfits for both cores.
        val result = base(shown, 3)
        assertEquals(3, result.size)
        assertTrue(result.none { it.key() in shown }, "only unseen outfits: ${result.map { it.key() }}")
        assertEquals(2, result.map { it.core() }.toSet().size)
    }

    @Test
    fun `excluding each core's best outfit still returns a full seeded page`() {
        val best = engine.recommend(manyShoesInput, topN = 2).map { it.key() }.toSet()
        repeat(20) { seed ->
            val result = engine.recommend(manyShoesInput, topN = 3, exclude = best, jitter = Random(seed))
            assertEquals(3, result.size)
            assertTrue(result.none { it.key() in best })
        }
    }

    // =========================================================================
    // Layering fairness
    // =========================================================================

    private val layeringItems = listOf(
        item(id = 1, role = "Top", warmthLayer = "Base", colorFamilies = setOf("Cool")),
        item(id = 2, role = "Bottom", warmthLayer = "Base", colorFamilies = setOf("Neutral")),
        item(id = 3, role = "Outerwear", warmthLayer = "Outer", colorFamilies = setOf("Earth")),
        item(id = 4, role = "Footwear", colorFamilies = setOf("Neutral")),
    )

    @Test
    fun `cold weather puts an ordinary jacket in the top result`() {
        val input = engineInput(candidates = layeringItems, weather = weather(tempLow = -2.0, tempHigh = 3.0))
        val top = engine.recommend(input).first()
        assertTrue(top.items.any { it.outfitRole == "Outerwear" }, "top was ${top.key()}")
    }

    @Test
    fun `warm weather leaves the jacket out of the top result`() {
        val input = engineInput(candidates = layeringItems, weather = weather(tempLow = 22.0, tempHigh = 30.0))
        val top = engine.recommend(input).first()
        assertTrue(top.items.none { it.outfitRole == "Outerwear" }, "top was ${top.key()}")
    }
}
