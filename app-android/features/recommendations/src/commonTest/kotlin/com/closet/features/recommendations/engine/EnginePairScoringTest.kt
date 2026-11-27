package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import kotlinx.datetime.LocalDate
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Engine v2 (#96) pair scoring: co-wear history blended with the taste rules, the formality gap
 * and hue harmony.
 */
class EnginePairScoringTest {

    private val engine = OutfitRecommendationEngine()

    private fun List<OutfitCombo>.scoreOf(vararg ids: Long): Double =
        assertNotNull(find { it.key() == ids.toSet() }, "no combo ${ids.toList()} in ${map { it.key() }}").score

    private fun List<OutfitCombo>.rankOf(vararg ids: Long): Int =
        indexOfFirst { it.key() == ids.toSet() }.also { assertTrue(it >= 0, "no combo ${ids.toList()}") }

    // =========================================================================
    // Cold-start parity (Review Focus 1)
    // =========================================================================

    @Test
    fun `an all-empty CoWearStats gives identical combos and scores`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")),
            item(id = 2, role = "Top", colorFamilies = setOf("Bright"), isPatternSolid = false),
            item(id = 3, role = "Top", colorFamilies = setOf("Cool")),
            item(id = 4, role = "Bottom", colorFamilies = setOf("Cool")),
            item(id = 5, role = "Bottom", colorFamilies = setOf("Bright"), isPatternSolid = false),
            item(id = 6, role = "Bottom", colorFamilies = setOf("Earth")),
            item(id = 7, role = "OnePiece", colorFamilies = setOf("Warm")),
            item(id = 8, role = "Outerwear", warmthLayer = "Outer", colorFamilies = setOf("Earth")),
            item(id = 9, role = "Footwear", colorFamilies = setOf("Neutral")),
            item(id = 10, role = "Footwear", colorFamilies = setOf("Bright")),
            item(id = 11, role = "Accessory", colorFamilies = setOf("Warm"), isPatternSolid = false),
        )
        val base = engineInput(
            candidates = items,
            weather = weather(tempLow = 2.0, tempHigh = 4.0),
            today = LocalDate(2026, 3, 31),
            lastWornDates = mapOf(
                1L to ItemLastWorn(1, "2026-03-30"),
                4L to ItemLastWorn(4, "2026-01-01"),
                9L to ItemLastWorn(9, "2026-03-20"),
            ),
        )
        val noHistory = engine.recommend(base, topN = 25)
        assertTrue(noHistory.size > 5)
        val emptyStats = CoWearStats(pairCounts = emptyMap(), itemCounts = emptyMap(), total = 0.0)
        assertEquals(noHistory, engine.recommend(base.copy(coWear = emptyStats), topN = 25))
        // Logs exist but none involve these items: still no evidence for any pair.
        val unrelated = CoWearStats(pairCounts = emptyMap(), itemCounts = emptyMap(), total = 40.0)
        assertEquals(noHistory, engine.recommend(base.copy(coWear = unrelated), topN = 25))
    }

    // =========================================================================
    // History
    // =========================================================================

    @Test
    fun `a pair logged together ranks above an otherwise equal pair`() {
        val items = listOf(item(id = 1, role = "Top"), item(id = 2, role = "Bottom"), item(id = 3, role = "Bottom"))
        // Without history the two cores tie at 1_0.
        val cold = engine.recommend(engineInput(candidates = items))
        assertEquals(cold.scoreOf(1, 2), cold.scoreOf(1, 3))

        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 5.0),
            itemCounts = mapOf(1L to 5.0, 2L to 5.0, 3L to 5.0),
            total = 20.0,
        )
        val result = engine.recommend(engineInput(candidates = items, coWear = coWear))
        assertEquals(setOf(1L, 2L), result.first().key())
        // A+B: E = 1_25, lift clamps to 1_3, weight 0_5 -> 0_5 x 1_3 + 0_5 x 1_0 = 1_15
        assertEquals(1.15, result.scoreOf(1, 2), 1e-9)
        // A+C: worn often apart, lift clamps to 0_7, weight 0_2 -> 0_2 x 0_7 + 0_8 = 0_94
        assertEquals(0.94, result.scoreOf(1, 3), 1e-9)
    }

    @Test
    fun `habit outvotes a penalising rule`() {
        // Color harmony no longer goes below 1 (no clash band, #81), so the rule here is the
        // formality gap.
        val items = listOf(
            item(id = 1, role = "Top", formalityLevel = 5.0),
            item(id = 2, role = "Bottom", formalityLevel = 1.0),  // a gap of 4: rules give 0_85
            item(id = 3, role = "Bottom"),                        // harmless, never worn
        )
        val cold = engine.recommend(engineInput(candidates = items))
        assertTrue(cold.rankOf(1, 3) < cold.rankOf(1, 2), "rules alone prefer the harmless bottom")

        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 20.0),
            itemCounts = mapOf(1L to 20.0, 2L to 20.0),
            total = 100.0,
        )
        val result = engine.recommend(engineInput(candidates = items, coWear = coWear))
        assertEquals(setOf(1L, 2L), result.first().key())
        // W = 0_7, H = 1_3, R = 0_85 -> 0_91 + 0_255 = 1_165
        assertEquals(1.165, result.scoreOf(1, 2), 1e-9)
        assertEquals(1.0, result.scoreOf(1, 3), 1e-9)
    }

    @Test
    fun `similar items supply pair signal when direct co-wear history is sparse`() {
        // Items 1 (Top) and 2 (Bottom) are brand new (zero direct wear logs).
        // Items 1 and 3 (Bottom) have equal rules compatibility (both 1.0).
        // Item 1 is very similar to Top 10 (ItemNeighbor with sim = 0.9).
        // Item 2 is very similar to Bottom 20 (ItemNeighbor with sim = 0.85).
        // Top 10 and Bottom 20 have heavy co-wear history.
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Bottom"),
        )
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(10L, 0.9)),
            2L to listOf(ItemNeighbor(20L, 0.85)),
        )
        val coWear = CoWearStats(
            pairCounts = mapOf((10L to 20L) to 20.0),
            itemCounts = mapOf(10L to 20.0, 20L to 20.0),
            total = 50.0,
        )
        val input = engineInput(
            candidates = items,
            coWear = coWear,
            itemNeighbors = neighbors,
        )
        val result = engine.recommend(input)
        assertEquals(setOf(1L, 2L), result.first().key(), "neighbor wear history lifts unseen core (1, 2) above (1, 3)")
        assertTrue(result.scoreOf(1, 2) > result.scoreOf(1, 3))
    }

    @Test
    fun `subcategory co-wear supplies pair signal when direct and neighbor history are absent`() {
        // Items 1 (Top, subcat 101) and 2 (Bottom, subcat 202) have zero direct logs and no neighbors.
        // Item 3 (Bottom, subcat 303) has zero direct logs and no subcat co-wear.
        val items = listOf(
            item(id = 1, role = "Top", subcategoryId = 101),
            item(id = 2, role = "Bottom", subcategoryId = 202),
            item(id = 3, role = "Bottom", subcategoryId = 303),
        )
        val subCoWear = CoWearStats(
            pairCounts = mapOf((101L to 202L) to 15.0),
            itemCounts = mapOf(101L to 20.0, 202L to 20.0),
            total = 50.0,
        )
        val input = engineInput(
            candidates = items,
            subcategoryCoWear = subCoWear,
        )
        val result = engine.recommend(input)
        assertEquals(setOf(1L, 2L), result.first().key(), "subcategory co-wear lifts core (1, 2) above (1, 3)")
        assertTrue(result.scoreOf(1, 2) > result.scoreOf(1, 3))
    }

    // =========================================================================
    // Formality
    // =========================================================================

    @Test
    fun `a formal blazer with sweatpants ranks below the blazer with trousers`() {
        val items = listOf(
            item(id = 1, role = "Top").copy(name = "Blazer", formalityLevel = 5.0),
            item(id = 2, role = "Bottom").copy(name = "Sweatpants", formalityLevel = 1.0),
            item(id = 3, role = "Bottom").copy(name = "Trousers", formalityLevel = 4.0),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(setOf(1L, 3L), result.first().key())
        assertEquals(1.0, result.scoreOf(1, 3), 1e-9)
        assertEquals(0.85, result.scoreOf(1, 2), 1e-9)
    }

    // =========================================================================
    // Hue harmony
    // =========================================================================

    @Test
    fun `complementary hexes outrank an unrelated pair`() {
        val items = listOf(
            item(id = 1, role = "Top").inColors("Red"),
            item(id = 2, role = "Bottom").inColors("Royal Blue"),
            item(id = 3, role = "Bottom", subcategoryId = 9).inColors("Forest Green"),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(setOf(1L, 3L), result.first().key())
        assertEquals(1.05, result.scoreOf(1, 3), 1e-9)  // about 168 degrees: complementary
        assertEquals(1.0, result.scoreOf(1, 2), 1e-9)   // about 110 degrees: no effect, no clash
    }

    @Test
    fun `an item with no hex sits out of harmony`() {
        val items = listOf(
            item(id = 1, role = "Top").inColors("Red"),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Cool")),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1.0, result.single().score, 1e-9)
    }

    @Test
    fun `same-family pairs use max of family rule and hue term`() {
        // Red and Yellow are both Warm, about 106 degrees apart on the painter's wheel.
        // The hue term alone gives 1.0, but Warm + Warm gives 1.10.
        val items = listOf(
            item(id = 1, role = "Top").inColors("Red"),
            item(id = 2, role = "Bottom").inColors("Yellow"),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1.10, result.single().score, 1e-9)
    }

    @Test
    fun `measured shares weight the harmony`() {
        // A grey tee with a 30 percent red print and forest green chinos: 1_05 ^ 0_3.
        val tee = item(id = 1, role = "Top", colorFamilies = setOf("Neutral", "Warm")).copy(
            colorHexes = listOf(
                ColorHex("#808080", "Neutral", share = 0.7, wearsAsNeutral = true),
                ColorHex("#C41E3A", "Warm", share = 0.3),
            ),
        )
        val chinos = item(id = 2, role = "Bottom").inColors("Forest Green")
        val result = engine.recommend(engineInput(candidates = listOf(tee, chinos)))
        assertEquals(1.05.pow(0.3), result.single().score, 1e-9)
    }
}
