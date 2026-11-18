package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ranking behaviour added in #94: harmony geometric mean, the footwear penalty, wear recency,
 * exclusion of already-shown combos, ranking-only randomness and diverse cores.
 */
class EngineRankingTest {

    private val engine = OutfitRecommendationEngine()

    // =========================================================================
    // Harmony — geometric mean of the pair multipliers
    // =========================================================================

    @Test
    fun `three Warm items score the geometric mean 1_10 not the product 1_331`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Warm")),
            item(id = 3, role = "Outerwear", colorFamilies = setOf("Warm"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        val threeItem = assertNotNull(result.find { it.items.size == 3 })
        // 3 pairs of 1.10 → (1.10^3)^(1/3) = 1.10
        assertEquals(1.10, threeItem.score, 0.001)
    }

    @Test
    fun `Warm Warm Cool scores the geometric mean of mixed pairs`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Warm")),
            item(id = 3, role = "Outerwear", colorFamilies = setOf("Cool"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        val threeItem = assertNotNull(result.find { it.items.size == 3 })
        // Pairs: Warm+Warm 1.10, Warm+Cool 0.85, Warm+Cool 0.85 → (1.10 × 0.85 × 0.85)^(1/3) ≈ 0.926
        assertEquals(0.926, threeItem.score, 0.001)
    }

    // =========================================================================
    // Footwear penalty
    // =========================================================================

    @Test
    fun `combo with shoes ranks first and shoe-less combo scores 0_85 of base`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Footwear")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue(result.first().items.any { it.outfitRole == "Footwear" })
        val shoeless = assertNotNull(result.find { combo -> combo.items.none { it.outfitRole == "Footwear" } })
        assertEquals(0.85 * 1.0, shoeless.score, 0.001)
    }

    @Test
    fun `no footwear in the pool gives no penalty`() {
        val items = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Bottom"))
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].score, 0.001)
    }

    // =========================================================================
    // Wear recency
    // =========================================================================

    private val today = LocalDate(2026, 3, 31)

    private fun onePieceScore(lastWorn: String?, today: LocalDate? = this.today): Double {
        val lastWornDates = if (lastWorn == null) {
            emptyMap()
        } else {
            mapOf(1L to ItemLastWorn(clothingItemId = 1, lastWornDate = lastWorn))
        }
        val result = engine.recommend(
            engineInput(
                candidates = listOf(item(id = 1, role = "OnePiece")),
                lastWornDates = lastWornDates,
                today = today
            )
        )
        assertEquals(1, result.size)
        return result[0].score
    }

    @Test
    fun `worn exactly 3 days ago is penalised 0_90`() {
        assertEquals(0.90, onePieceScore("2026-03-28"), 0.001)
    }

    @Test
    fun `worn 4 days ago is neutral`() {
        assertEquals(1.0, onePieceScore("2026-03-27"), 0.001)
    }

    @Test
    fun `worn 29 days ago is neutral`() {
        assertEquals(1.0, onePieceScore("2026-03-02"), 0.001)
    }

    @Test
    fun `worn exactly 30 days ago gets 1_05 bonus`() {
        assertEquals(1.05, onePieceScore("2026-03-01"), 0.001)
    }

    @Test
    fun `timestamp suffix is ignored when parsing the last-worn date`() {
        assertEquals(0.90, onePieceScore("2026-03-30T08:15:00"), 0.001)
    }

    @Test
    fun `never worn is neutral`() {
        assertEquals(1.0, onePieceScore(null), 0.001)
    }

    @Test
    fun `malformed last-worn date is neutral`() {
        assertEquals(1.0, onePieceScore("not-a-date"), 0.001)
    }

    @Test
    fun `future last-worn date is neutral`() {
        assertEquals(1.0, onePieceScore("2026-04-05"), 0.001)
    }

    @Test
    fun `null today disables recency`() {
        assertEquals(1.0, onePieceScore("2026-03-28", today = null), 0.001)
    }

    @Test
    fun `recency applies when weather is set`() {
        // Mild weather with no item signals → no weather multiplier; worn 2 days ago → 0.90.
        val result = engine.recommend(
            engineInput(
                candidates = listOf(item(id = 1, role = "OnePiece")),
                lastWornDates = mapOf(1L to ItemLastWorn(clothingItemId = 1, lastWornDate = "2026-03-29")),
                weather = weather(tempLow = 10.0, tempHigh = 20.0),
                today = today
            )
        )
        assertEquals(1, result.size)
        assertEquals(0.90, result[0].score, 0.001)
    }

    // =========================================================================
    // Exclusion
    // =========================================================================

    @Test
    fun `excluding the top combo key drops it and promotes the next best`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Footwear")
        )
        val input = engineInput(candidates = items)
        val best = engine.recommend(input).first()
        assertEquals(setOf(1L, 2L, 3L), best.key())

        val result = engine.recommend(input, exclude = setOf(best.key()))
        assertFalse(result.any { it.key() == best.key() })
        assertEquals(setOf(1L, 2L), result.first().key())
    }

    @Test
    fun `excluding the only result surfaces a different top`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Top"),
            item(id = 3, role = "Top"),
            item(id = 4, role = "Bottom")
        )
        val input = engineInput(candidates = items)
        val first = engine.recommend(input, topN = 1).single()
        val second = engine.recommend(input, topN = 1, exclude = setOf(first.key())).single()
        assertTrue(topIds(second).isNotEmpty())
        assertTrue(topIds(second).none { it in topIds(first) }, "Expected a new top, got ${second.key()}")
    }

    @Test
    fun `excluding a whole page rotates a new top into the next page`() {
        val items = (1L..5L).map { item(id = it, role = "Top") } +
            listOf(item(id = 6, role = "Bottom"), item(id = 7, role = "Bottom"))
        val input = engineInput(candidates = items)
        val first = engine.recommend(input, topN = 3)
        assertEquals(3, first.size)
        val second = engine.recommend(input, topN = 3, exclude = first.map { it.key() }.toSet())
        val firstTops = first.flatMap { topIds(it) }.toSet()
        assertTrue(
            second.flatMap { topIds(it) }.any { it !in firstTops },
            "Expected a top outside $firstTops, got ${second.map { it.key() }}"
        )
    }

    @Test
    fun `the only unseen combo is reachable when every item has been shown`() {
        // Every item has been shown and five of the six cores are excluded. Top 3 + bottom 5,
        // which the old per-slot cap of 2 left out of the pool, is the only unseen combo.
        val items = listOf(
            item(id = 1, role = "Top"), item(id = 2, role = "Top"), item(id = 3, role = "Top"),
            item(id = 4, role = "Bottom"), item(id = 5, role = "Bottom")
        )
        val input = engineInput(candidates = items)
        val exclude = setOf(
            setOf(1L, 4L), setOf(1L, 5L), setOf(2L, 4L), setOf(2L, 5L), setOf(3L, 4L)
        )
        val result = engine.recommend(input, topN = 1, exclude = exclude)
        assertEquals(listOf(setOf(3L, 5L)), result.map { it.key() })
    }

    @Test
    fun `exclusion of every combo still returns empty`() {
        val items = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Top"), item(id = 3, role = "Bottom"))
        val input = engineInput(candidates = items)
        val result = engine.recommend(input, exclude = setOf(setOf(1L, 3L), setOf(2L, 3L)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty exclude keeps the pre-change ordering`() {
        val input = engineInput(candidates = diversityItems)
        val expected = listOf(
            setOf(1L, 3L, 4L), setOf(2L, 3L, 4L), setOf(1L, 3L, 5L),
            setOf(2L, 3L, 5L), setOf(1L, 3L), setOf(2L, 3L)
        )
        assertEquals(expected, engine.recommend(input, topN = 6).map { it.key() })
        assertEquals(expected, engine.recommend(input, topN = 6, exclude = emptySet()).map { it.key() })
    }

    private fun topIds(combo: OutfitCombo): List<Long> =
        combo.items.filter { it.outfitRole == "Top" }.map { it.id }

    // =========================================================================
    // Diversity — no two results share a core, then fill
    // =========================================================================

    /** 2 tops × 1 bottom, plus 2 footwear → 2 cores, 6 combos. */
    private val diversityItems = listOf(
        item(id = 1, role = "Top"),
        item(id = 2, role = "Top"),
        item(id = 3, role = "Bottom"),
        item(id = 4, role = "Footwear"),
        item(id = 5, role = "Footwear")
    )

    private fun OutfitCombo.core(): Set<Long> =
        items.filter { it.outfitRole in setOf("Top", "Bottom", "OnePiece") }.map { it.id }.toSet()

    @Test
    fun `topN 2 returns two results with different cores`() {
        val result = engine.recommend(engineInput(candidates = diversityItems), topN = 2)
        assertEquals(2, result.size)
        assertTrue(result[0].core() != result[1].core(), "Results must not share a core")
    }

    @Test
    fun `topN 4 fills the remaining slots from skipped combos`() {
        val result = engine.recommend(engineInput(candidates = diversityItems), topN = 4)
        assertEquals(4, result.size)
        assertTrue(result[0].core() != result[1].core(), "Primary pass picks distinct cores first")
        assertEquals(2, result.map { it.core() }.toSet().size)
        assertEquals(4, result.map { it.key() }.toSet().size, "No combo is returned twice")
    }

    // =========================================================================
    // Selection randomness — ordering only
    // =========================================================================

    @Test
    fun `a seeded run keeps the un-noised scores`() {
        val input = engineInput(candidates = diversityItems)
        val plain = engine.recommend(input, topN = 6).associate { it.key() to it.score }
        val jittered = engine.recommend(input, topN = 6, jitter = Random(42))
            .associate { it.key() to it.score }
        assertEquals(6, plain.size)
        assertEquals(plain, jittered)
    }

    @Test
    fun `the same seed gives the identical order`() {
        val input = engineInput(candidates = diversityItems)
        val first = engine.recommend(input, topN = 6, jitter = Random(42)).map { it.key() }
        val second = engine.recommend(input, topN = 6, jitter = Random(42)).map { it.key() }
        assertEquals(first, second)
    }
}
