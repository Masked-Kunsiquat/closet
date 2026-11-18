package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import kotlinx.datetime.LocalDate
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
        val noHistory = engine.recommend(base, topN = 25, candidatesPerSlot = 3)
        assertTrue(noHistory.size > 5)
        val emptyStats = CoWearStats(pairCounts = emptyMap(), itemCounts = emptyMap(), total = 0.0)
        assertEquals(noHistory, engine.recommend(base.copy(coWear = emptyStats), topN = 25, candidatesPerSlot = 3))
        // Logs exist but none involve these items: still no evidence for any pair.
        val unrelated = CoWearStats(pairCounts = emptyMap(), itemCounts = emptyMap(), total = 40.0)
        assertEquals(noHistory, engine.recommend(base.copy(coWear = unrelated), topN = 25, candidatesPerSlot = 3))
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
    fun `habit outvotes a clashing color rule`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Bright")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Bright")),   // clashes: rules give 0_70
            item(id = 3, role = "Bottom", colorFamilies = setOf("Neutral")),  // harmless, never worn
        )
        val cold = engine.recommend(engineInput(candidates = items))
        assertTrue(cold.rankOf(1, 3) < cold.rankOf(1, 2), "rules alone prefer the neutral bottom")

        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 20.0),
            itemCounts = mapOf(1L to 20.0, 2L to 20.0),
            total = 100.0,
        )
        val result = engine.recommend(engineInput(candidates = items, coWear = coWear))
        assertEquals(setOf(1L, 2L), result.first().key())
        // W = 0_7, H = 1_3, R = 0_7 -> 0_91 + 0_21 = 1_12
        assertEquals(1.12, result.scoreOf(1, 2), 1e-9)
        assertEquals(1.0, result.scoreOf(1, 3), 1e-9)
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
    fun `complementary hexes outrank clashing hexes`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")).copy(colorHexes = listOf("#FF0000")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Cool")).copy(colorHexes = listOf("#00FF00")),
            item(id = 3, role = "Bottom", colorFamilies = setOf("Cool"), subcategoryId = 9)
                .copy(colorHexes = listOf("#00FFFF")),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(setOf(1L, 3L), result.first().key())
        assertEquals(1.05, result.scoreOf(1, 3), 1e-9)  // complementary, replaces Warm+Cool 0_85
        assertEquals(0.90, result.scoreOf(1, 2), 1e-9)  // 120 degree clash
    }

    @Test
    fun `a pair with one missing hex falls back to the family rule`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")).copy(colorHexes = listOf("#FF0000")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Cool")),
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(0.85, result.single().score, 1e-9)
    }
}
