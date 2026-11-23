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

    /**
     * One core (1 + 2), eight shoes and no accessories. Shoes 3-6 were last worn 40 days ago
     * (x 1.05), shoes 7-10 ten days ago (x 1.0), so the core's footwear beam is exactly shoes 3-6.
     */
    private val staleShoesInput = engineInput(
        candidates = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Bottom")) +
            (3L..10L).map { item(id = it, role = "Footwear") },
        lastWornDates = (3L..6L).associateWith { ItemLastWorn(it, "2026-02-19") } +
            (7L..10L).associateWith { ItemLastWorn(it, "2026-03-21") },
        today = today,
    )

    private fun assembleAtBase(input: EngineInput, topN: Int, exclude: Set<Set<Long>>) =
        engine.assemble(
            input, topN = topN, exclude = exclude, random = null,
            coreBeam = OutfitRecommendationEngine.CORE_BEAM,
            beamPerCore = OutfitRecommendationEngine.BEAM_PER_CORE,
        )

    @Test
    fun `with no accessories exclusion applies at the footwear step`() {
        val beam = assembleAtBase(staleShoesInput, topN = 10, exclude = emptySet()).map { it.key() }
        assertEquals((3L..6L).map { setOf(1L, 2L, it) }.toSet(), beam.toSet(), "the footwear beam is shoes 3-6")

        // The four best outfits are shown: they must not take the beam's places.
        val result = assembleAtBase(staleShoesInput, topN = 1, exclude = beam.toSet())
        assertEquals(1, result.size, "the base beam still finds an unseen outfit")
        val shoe = result.single().items.single { it.outfitRole == "Footwear" }.id
        assertTrue(shoe in 7L..10L, "expected an unshown shoe, got ${result.single().key()}")
    }

    @Test
    fun `with no optional items at all exclusion applies to the cores`() {
        val input = engineInput(
            candidates = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Top"), item(id = 3, role = "Bottom")),
        )
        val result = assembleAtBase(input, topN = 2, exclude = setOf(setOf(1L, 3L)))
        assertEquals(listOf(setOf(2L, 3L)), result.map { it.key() })
    }

    /** One core (1 + 2), six shoes whose co-wear with the top differs, so every score is distinct. */
    private val distinctShoesInput = engineInput(
        candidates = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Bottom")) +
            (3L..8L).map { item(id = it, role = "Footwear") },
        coWear = CoWearStats(
            pairCounts = (3L..8L).associate { (1L to it) to it.toDouble() },
            itemCounts = mapOf(1L to 45.0) + (3L..8L).associateWith { it.toDouble() },
            total = 60.0,
        ),
    )

    @Test
    fun `widening rescues a page the base beam leaves short`() {
        val ranked = assembleAtBase(distinctShoesInput, topN = 10, exclude = emptySet())
        assertEquals(ranked.size, ranked.map { it.score }.toSet().size, "scores are distinct")
        val shown = ranked.take(2).map { it.key() }.toSet()

        // 7 outfits (none + 6 shoes) minus 2 shown leaves 5, but one core's base beam holds 4.
        val base = assembleAtBase(distinctShoesInput, topN = 5, exclude = shown)
        assertEquals(4, base.size, "the base beam is short")

        val widened = engine.recommend(distinctShoesInput, topN = 5, exclude = shown)
        assertEquals(5, widened.size)
        assertTrue(widened.none { it.key() in shown })
        val rescued = widened.map { it.key() } - base.map { it.key() }.toSet()
        assertEquals(1, rescued.size, "widening adds the one unseen outfit the base beam dropped")
    }

    @Test
    fun `widening supplies more outfits than base beam without exclusions`() {
        val base = assembleAtBase(distinctShoesInput, topN = 6, exclude = emptySet())
        assertEquals(4, base.size)

        val widened = engine.recommend(distinctShoesInput, topN = 6, exclude = emptySet())
        assertEquals(6, widened.size)
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

    /**
     * More jackets (5) and shoes (4) than the beam (4) holds, so the outerwear and footwear
     * steps decide whether a jacket survives, and an accessory slot comes after them. If
     * layering waited for the complete outfit, the jacket-less partials with shoes would fill
     * the beam (cold) or the flattering jackets would (warm).
     */
    private fun layeringPlacementItems(topColor: String, jacketColor: String, patterned: Boolean = false) =
        listOf(
            item(id = 1, role = "Top", warmthLayer = "Base", isPatternSolid = !patterned, subcategoryId = 1)
                .inColors(topColor),
            item(id = 2, role = "Bottom", warmthLayer = "Base", colorFamilies = setOf("Neutral"), subcategoryId = 2),
        ) + (3L..7L).map {
            item(id = it, role = "Outerwear", warmthLayer = "Outer", isPatternSolid = !patterned, subcategoryId = it)
                .inColors(jacketColor)
        } + (8L..11L).map {
            item(id = it, role = "Footwear", colorFamilies = setOf("Neutral"), subcategoryId = it)
        } + item(id = 12, role = "Accessory", colorFamilies = setOf("Neutral"), subcategoryId = 12)

    @Test
    fun `cold weather keeps a mediocre jacket through a full footwear beam`() {
        // Patterned top + patterned jacket (x 0.75), still better than no layer (x 1.10 for the
        // one pattern, x 0.50 for the cold = 0.55).
        val input = engineInput(
            candidates = layeringPlacementItems(topColor = "Grey", jacketColor = "Grey", patterned = true),
            weather = weather(tempLow = -2.0, tempHigh = 3.0),
        )
        val top = engine.recommend(input).first()
        assertTrue(top.items.any { it.outfitRole == "Outerwear" }, "top was ${top.key()}")
        assertEquals(0.75, top.score, 0.001)
    }

    @Test
    fun `warm weather drops a flattering jacket through a full footwear beam`() {
        // Red top + Burgundy jacket (same family, x 1.10) loses to no layer once the warm
        // penalty (x 0.75) applies.
        val input = engineInput(
            candidates = layeringPlacementItems(topColor = "Red", jacketColor = "Burgundy"),
            weather = weather(tempLow = 22.0, tempHigh = 30.0),
        )
        val top = engine.recommend(input).first()
        assertTrue(top.items.none { it.outfitRole == "Outerwear" }, "top was ${top.key()}")
        assertEquals(1.0, top.score, 0.001)
    }

    @Test
    fun `warm weather leaves the jacket out of the top result`() {
        val input = engineInput(candidates = layeringItems, weather = weather(tempLow = 22.0, tempHigh = 30.0))
        val top = engine.recommend(input).first()
        assertTrue(top.items.none { it.outfitRole == "Outerwear" }, "top was ${top.key()}")
    }
}
