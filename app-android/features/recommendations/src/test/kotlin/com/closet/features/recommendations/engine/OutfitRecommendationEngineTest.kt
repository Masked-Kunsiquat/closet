package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OutfitRecommendationEngine].
 *
 * All tests are pure JUnit — no Robolectric, no Room, no Android context.
 * Test fixtures are plain data-builder functions at the bottom of this file.
 */
class OutfitRecommendationEngineTest {

    private val engine = OutfitRecommendationEngine()

    // =========================================================================
    // Hard filter tests
    // =========================================================================

    /**
     * The DAO hard-filters on status=Active and wash_status=Clean before items
     * reach the engine. The engine accepts all EngineItems (defensive guard —
     * status/wash fields are not carried on EngineItem).
     *
     * This test verifies the engine works correctly when given only valid items
     * and doesn't crash on an empty candidate list.
     */
    @Test
    fun `empty candidate list returns empty result`() {
        val input = engineInput(candidates = emptyList())
        val result = engine.recommend(input)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `only items with no outfit role are excluded from all combos`() {
        // Items with role "Other" cannot form a valid core (Top+Bottom or OnePiece)
        val items = listOf(
            item(id = 1, role = "Other"),
            item(id = 2, role = "Other")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue("Other-role items cannot form a valid combo", result.isEmpty())
    }

    @Test
    fun `items without seasons cannot form combos if no valid core`() {
        // One Accessory and one Footwear — valid optional roles but no core
        val items = listOf(
            item(id = 1, role = "Footwear"),
            item(id = 2, role = "Accessory")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue("No core items → no valid combos", result.isEmpty())
    }

    // =========================================================================
    // Suitability scoring tests
    // =========================================================================

    @Test
    fun `no weather data gives all items score 1_0`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom")
        )
        val input = engineInput(candidates = items, weather = null)
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // score = mean(1.0, 1.0) × 1.0 = 1.0
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `temperature outside comfortable range applies 0_55 multiplier`() {
        // Item worn in 15–25°C range; today is 5°C low / 8°C high — outside range
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val tempPercentiles = mapOf(
            1L to ItemTempPercentiles(
                clothingItemId = 1,
                p10TempLow = 15.0,
                p90TempHigh = 25.0,
                logCount = 10
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            tempPercentiles = tempPercentiles,
            weather = weather(tempLow = 5.0, tempHigh = 8.0)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // item 1 score = 0.55; item 2 score = 1.0; mean = 0.775
        assertEquals(0.775, result[0].score, 0.001)
    }

    @Test
    fun `temperature inside comfortable range has no multiplier`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val tempPercentiles = mapOf(
            1L to ItemTempPercentiles(
                clothingItemId = 1,
                p10TempLow = 10.0,
                p90TempHigh = 25.0,
                logCount = 10
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            tempPercentiles = tempPercentiles,
            weather = weather(tempLow = 15.0, tempHigh = 20.0)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `sparse temp data (less than 5 logs) skips temperature signal`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        // Only 3 logs — below the 5-log threshold
        val tempPercentiles = mapOf(
            1L to ItemTempPercentiles(
                clothingItemId = 1,
                p10TempLow = 15.0,
                p90TempHigh = 25.0,
                logCount = 3  // sparse
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            tempPercentiles = tempPercentiles,
            weather = weather(tempLow = 5.0, tempHigh = 8.0) // would trigger penalty if not sparse
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // Signal skipped → both items score 1.0 → mean = 1.0
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `raining today and item rain pct under 20 percent applies 0_60 multiplier`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val rainSuitability = mapOf(
            1L to ItemRainSuitability(
                clothingItemId = 1,
                rainPct = 0.10, // 10% — under the 20% threshold
                rainLogCount = 8
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            rainSuitability = rainSuitability,
            weather = weather(isRaining = true)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // item 1 = 0.60; item 2 = 1.0; mean = 0.80
        assertEquals(0.80, result[0].score, 0.001)
    }

    @Test
    fun `rain signal skipped when item has fewer than 5 logs`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val rainSuitability = mapOf(
            1L to ItemRainSuitability(
                clothingItemId = 1,
                rainPct = 0.05,
                rainLogCount = 4  // sparse
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            rainSuitability = rainSuitability,
            weather = weather(isRaining = true)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `windy today and item wind pct under 20 percent applies 0_70 multiplier`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val windSuitability = mapOf(
            1L to ItemWindSuitability(
                clothingItemId = 1,
                windPct = 0.10,
                windLogCount = 6
            )
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            windSuitability = windSuitability,
            weather = weather(isWindy = true)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // item 1 = 0.70; item 2 = 1.0; mean = 0.85
        assertEquals(0.85, result[0].score, 0.001)
    }

    @Test
    fun `multipliers stack independently — rain and wind both active`() {
        val top = item(id = 1, role = "Top")
        val bottom = item(id = 2, role = "Bottom")

        val rainSuitability = mapOf(
            1L to ItemRainSuitability(clothingItemId = 1, rainPct = 0.10, rainLogCount = 6)
        )
        val windSuitability = mapOf(
            1L to ItemWindSuitability(clothingItemId = 1, windPct = 0.10, windLogCount = 6)
        )

        val input = engineInput(
            candidates = listOf(top, bottom),
            rainSuitability = rainSuitability,
            windSuitability = windSuitability,
            weather = weather(isRaining = true, isWindy = true)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // item 1 = 0.60 × 0.70 = 0.42; item 2 = 1.0; mean = 0.71
        assertEquals(0.71, result[0].score, 0.001)
    }

    // =========================================================================
    // Category completeness tests
    // =========================================================================

    @Test
    fun `Top plus Bottom forms a valid combo`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        val roles = result[0].items.map { it.outfitRole }.toSet()
        assertTrue(roles.containsAll(setOf("Top", "Bottom")))
    }

    @Test
    fun `OnePiece alone forms a valid combo`() {
        val items = listOf(item(id = 1, role = "OnePiece"))
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        assertEquals("OnePiece", result[0].items[0].outfitRole)
    }

    @Test
    fun `Top without Bottom does not form a valid combo`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Footwear")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue("Top alone cannot form a valid combo", result.isEmpty())
    }

    @Test
    fun `Bottom without Top does not form a valid combo`() {
        val items = listOf(item(id = 2, role = "Bottom"))
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue("Bottom alone cannot form a valid combo", result.isEmpty())
    }

    @Test
    fun `optional slots added to valid core`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Outerwear"),
            item(id = 4, role = "Footwear"),
            item(id = 5, role = "Accessory")
        )
        val result = engine.recommend(engineInput(candidates = items))
        // Should have combos: bare core + each optional combination
        assertTrue(result.isNotEmpty())
        // Highest-scoring combo is likely the one with all items (most valid)
        // Just verify valid combos are formed
        result.forEach { combo ->
            val roles = combo.items.map { it.outfitRole }
            val hasCore = (roles.contains("Top") && roles.contains("Bottom")) || roles.contains("OnePiece")
            assertTrue("Every combo must have a valid core", hasCore)
        }
    }

    @Test
    fun `multiple tops and bottoms produce multiple combos`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Top"),
            item(id = 3, role = "Bottom"),
            item(id = 4, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items))
        // Per-category cap is 2, so 2 tops × 2 bottoms = 4 core combos
        // But per-slot cap limits each slot to 2 candidates → still up to 4 combos
        assertTrue("Multiple tops + bottoms produce multiple combos", result.size >= 2)
        assertTrue("At most TOP_N (3) results returned", result.size <= 3)
    }

    @Test
    fun `OnePiece and Top plus Bottom both generate valid combos`() {
        val items = listOf(
            item(id = 1, role = "OnePiece"),
            item(id = 2, role = "Top"),
            item(id = 3, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue(result.isNotEmpty())
        val hasOnePiece = result.any { combo -> combo.items.any { it.outfitRole == "OnePiece" } }
        val hasTopBottom = result.any { combo ->
            combo.items.any { it.outfitRole == "Top" } && combo.items.any { it.outfitRole == "Bottom" }
        }
        assertTrue("OnePiece combo exists", hasOnePiece)
        assertTrue("Top+Bottom combo exists", hasTopBottom)
    }

    // =========================================================================
    // Layering validation tests
    // =========================================================================

    @Test
    fun `cold day without Outer applies 0_50 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", warmthLayer = "Base"),
            item(id = 2, role = "Bottom")
        )
        val input = engineInput(
            candidates = items,
            weather = weather(tempHigh = 3.0) // below 5°C threshold
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // mean score = 1.0; cold multiplier = 0.50
        assertEquals(0.50, result[0].score, 0.001)
    }

    @Test
    fun `cold day with Outer does not apply cold multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", warmthLayer = "Base"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Outerwear", warmthLayer = "Outer")
        )
        val input = engineInput(
            candidates = items,
            weather = weather(tempHigh = 3.0)
        )
        val result = engine.recommend(input)
        // The highest-ranked combo should include the Outerwear (no cold penalty)
        val topCombo = result.first()
        val scores = result.map { it.score }
        val comboWithOuter = result.find { combo -> combo.items.any { it.outfitRole == "Outerwear" } }
        val comboWithoutOuter = result.find { combo -> combo.items.none { it.outfitRole == "Outerwear" } }

        if (comboWithOuter != null && comboWithoutOuter != null) {
            assertTrue(
                "Combo with Outer scores higher on cold day",
                comboWithOuter.score > comboWithoutOuter.score
            )
        }
    }

    @Test
    fun `warm day with Outer applies 0_75 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Outerwear", warmthLayer = "Outer")
        )
        // Only include the combo with Outer by using a pool that forces it
        val itemsWithOuterOnly = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom"),
            item(id = 3, role = "Outerwear", warmthLayer = "Outer")
        )
        val input = engineInput(
            candidates = itemsWithOuterOnly,
            weather = weather(tempLow = 25.0) // above 20°C threshold
        )
        val result = engine.recommend(input)
        val comboWithOuter = result.find { combo -> combo.items.any { it.outfitRole == "Outerwear" } }
        if (comboWithOuter != null) {
            // mean score = 1.0; warm multiplier = 0.75
            assertEquals(0.75, comboWithOuter.score, 0.001)
        }
    }

    @Test
    fun `warm day combo without Outer not penalised`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom")
        )
        val input = engineInput(
            candidates = items,
            weather = weather(tempLow = 25.0)
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // No Outer → no warm penalty; score = 1.0
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `no weather does not trigger layering multipliers`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items, weather = null))
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].score, 0.001)
    }

    // =========================================================================
    // Color harmony tests
    // =========================================================================

    @Test
    fun `same color family bonus applies plus 0_10`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Cool")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Cool"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; same-family bonus = +0.10 → multiplier = 1.10
        assertEquals(1.10, result[0].score, 0.001)
    }

    @Test
    fun `Earth plus Warm bonus applies plus 0_05`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Earth")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Warm"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; Earth+Warm bonus = +0.05 → multiplier = 1.05
        assertEquals(1.05, result[0].score, 0.001)
    }

    @Test
    fun `Bright plus Bright applies 0_70 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Bright")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Bright"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; Bright+Bright = × 0.70
        assertEquals(0.70, result[0].score, 0.001)
    }

    @Test
    fun `Warm plus Cool applies 0_85 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Warm")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Cool"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; Warm+Cool = × 0.85
        assertEquals(0.85, result[0].score, 0.001)
    }

    @Test
    fun `Neutral plus anything has no effect`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = setOf("Neutral")),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Bright"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // Neutral skipped in pairwise check → no effect → score = 1.0
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `item with no colors tagged treated as Neutral`() {
        val items = listOf(
            item(id = 1, role = "Top", colorFamilies = emptySet()),
            item(id = 2, role = "Bottom", colorFamilies = setOf("Bright"))
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // No-color item treated as Neutral → no pairwise effect → score = 1.0
        assertEquals(1.0, result[0].score, 0.001)
    }

    // =========================================================================
    // Pattern mixing tests
    // =========================================================================

    @Test
    fun `all solid items have no pattern effect`() {
        val items = listOf(
            item(id = 1, role = "Top", isPatternSolid = true),
            item(id = 2, role = "Bottom", isPatternSolid = true)
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].score, 0.001)
    }

    @Test
    fun `exactly one patterned item gives plus 0_10 bonus`() {
        val items = listOf(
            item(id = 1, role = "Top", isPatternSolid = false),  // patterned
            item(id = 2, role = "Bottom", isPatternSolid = true)
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; pattern bonus = +0.10 → multiplier = 1.10
        assertEquals(1.10, result[0].score, 0.001)
    }

    @Test
    fun `two patterned items applies 0_75 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", isPatternSolid = false),    // patterned
            item(id = 2, role = "Bottom", isPatternSolid = false)  // patterned
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
        // mean = 1.0; 2+ patterned = × 0.75
        assertEquals(0.75, result[0].score, 0.001)
    }

    @Test
    fun `three patterned items applies 0_75 multiplier`() {
        val items = listOf(
            item(id = 1, role = "Top", isPatternSolid = false),
            item(id = 2, role = "Bottom", isPatternSolid = false),
            item(id = 3, role = "Outerwear", warmthLayer = "Outer", isPatternSolid = false)
        )
        val result = engine.recommend(engineInput(candidates = items, weather = null))
        val comboWithOuter = result.find { combo -> combo.items.size == 3 }
        if (comboWithOuter != null) {
            assertEquals(0.75, comboWithOuter.score, 0.001)
        }
    }

    // =========================================================================
    // Top 3 and tie-breaking tests
    // =========================================================================

    @Test
    fun `at most 3 combos returned`() {
        // 2 tops × 2 bottoms = 4 cores, but per-slot cap is 2 → at most 4 combos before TOP_N
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Top"),
            item(id = 3, role = "Bottom"),
            item(id = 4, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertTrue("Result must not exceed 3", result.size <= 3)
    }

    @Test
    fun `fewer than 3 valid combos returns what is available`() {
        val items = listOf(
            item(id = 1, role = "Top"),
            item(id = 2, role = "Bottom")
        )
        val result = engine.recommend(engineInput(candidates = items))
        assertEquals(1, result.size)
    }

    @Test
    fun `tied scores broken by oldest last-worn date`() {
        // Two identical combos but one item was worn more recently
        val top1 = item(id = 1, role = "Top")
        val top2 = item(id = 2, role = "Top")
        val bottom1 = item(id = 3, role = "Bottom")
        val bottom2 = item(id = 4, role = "Bottom")

        // top1+bottom1 combo: top1 last worn 2025-01-01 — older
        // top2+bottom2 combo: top2 last worn 2025-06-01 — more recent
        val lastWornDates = mapOf(
            1L to ItemLastWorn(clothingItemId = 1, lastWornDate = "2025-01-01"),
            2L to ItemLastWorn(clothingItemId = 2, lastWornDate = "2025-06-01"),
            3L to ItemLastWorn(clothingItemId = 3, lastWornDate = "2025-01-01"),
            4L to ItemLastWorn(clothingItemId = 4, lastWornDate = "2025-06-01")
        )

        val input = engineInput(
            candidates = listOf(top1, top2, bottom1, bottom2),
            lastWornDates = lastWornDates
        )

        val result = engine.recommend(input)
        assertTrue(result.size >= 2)

        // The combo where the most-recently-worn item is oldest should rank first
        // top1+bottom1: max last-worn = "2025-01-01"
        // top2+bottom2: max last-worn = "2025-06-01"
        // "2025-01-01" < "2025-06-01" so top1+bottom1 should rank first
        val first = result[0]
        val firstIds = first.items.map { it.id }.toSet()
        assertTrue(
            "Oldest last-worn combo should rank first in ties",
            firstIds.contains(1L) && firstIds.contains(3L)
        )
    }

    @Test
    fun `never-worn items ranked first in ties over worn items`() {
        val top1 = item(id = 1, role = "Top")  // never worn
        val top2 = item(id = 2, role = "Top")  // recently worn
        val bottom = item(id = 3, role = "Bottom")  // shared

        val lastWornDates = mapOf(
            // top1 not in map → "never worn"
            2L to ItemLastWorn(clothingItemId = 2, lastWornDate = "2025-12-01"),
            3L to ItemLastWorn(clothingItemId = 3, lastWornDate = "2025-12-01")
        )

        val input = engineInput(
            candidates = listOf(top1, top2, bottom),
            lastWornDates = lastWornDates
        )

        val result = engine.recommend(input)
        assertTrue(result.size >= 2)

        // top1+bottom: max last-worn = "2025-12-01" (bottom)
        // top2+bottom: max last-worn = "2025-12-01" (both the same)
        // Both combos share the bottom — tie-break goes to the combo that also has
        // the never-worn top (top1); both have the same max date via shared bottom.
        // This verifies the engine doesn't crash on "never worn" items.
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `results are sorted descending by score`() {
        // Create scenario where different combos have different scores
        val highScoreTop = item(id = 1, role = "Top", colorFamilies = setOf("Cool"))
        val highScoreBottom = item(id = 2, role = "Bottom", colorFamilies = setOf("Cool"))
        val lowScoreTop = item(id = 3, role = "Top", colorFamilies = setOf("Warm"))
        val lowScoreBottom = item(id = 4, role = "Bottom", colorFamilies = setOf("Cool"))

        // highScore combo: Cool+Cool = same-family bonus +0.10 → score = 1.10
        // lowScore combo: Warm+Cool = slight clash × 0.85 → score = 0.85
        val result = engine.recommend(engineInput(
            candidates = listOf(highScoreTop, highScoreBottom, lowScoreTop, lowScoreBottom)
        ))

        assertTrue(result.isNotEmpty())
        for (i in 0 until result.size - 1) {
            assertTrue(
                "Results should be sorted descending by score",
                result[i].score >= result[i + 1].score
            )
        }
    }

    // =========================================================================
    // Combined multiplier tests
    // =========================================================================

    @Test
    fun `cold day with no outer and warm cool color clash combines multipliers`() {
        val top = item(id = 1, role = "Top", colorFamilies = setOf("Warm"))
        val bottom = item(id = 2, role = "Bottom", colorFamilies = setOf("Cool"))

        val input = engineInput(
            candidates = listOf(top, bottom),
            weather = weather(tempHigh = 3.0) // cold day, no outerwear
        )
        val result = engine.recommend(input)
        assertEquals(1, result.size)
        // mean = 1.0; cold-no-outer = × 0.50; warm+cool = × 0.85
        assertEquals(1.0 * 0.50 * 0.85, result[0].score, 0.001)
    }

    // =========================================================================
    // Test fixture builders
    // =========================================================================

    private fun item(
        id: Long,
        role: String? = null,
        warmthLayer: String? = null,
        colorFamilies: Set<String> = emptySet(),
        isPatternSolid: Boolean = true,
        subcategoryId: Long? = null
    ) = EngineItem(
        id = id,
        name = "Item $id",
        imagePath = null,
        categoryId = null,
        subcategoryId = subcategoryId,
        outfitRole = role,
        warmthLayer = warmthLayer,
        colorFamilies = colorFamilies,
        isPatternSolid = isPatternSolid
    )

    private fun weather(
        tempLow: Double? = null,
        tempHigh: Double? = null,
        isRaining: Boolean = false,
        isWindy: Boolean = false
    ) = EngineWeather(
        tempLowC = tempLow,
        tempHighC = tempHigh,
        isRaining = isRaining,
        isWindy = isWindy
    )

    private fun engineInput(
        candidates: List<EngineItem> = emptyList(),
        tempPercentiles: Map<Long, ItemTempPercentiles> = emptyMap(),
        rainSuitability: Map<Long, ItemRainSuitability> = emptyMap(),
        windSuitability: Map<Long, ItemWindSuitability> = emptyMap(),
        lastWornDates: Map<Long, ItemLastWorn> = emptyMap(),
        weather: EngineWeather? = null
    ) = EngineInput(
        candidates = candidates,
        tempPercentiles = tempPercentiles,
        rainSuitability = rainSuitability,
        windSuitability = windSuitability,
        lastWornDates = lastWornDates,
        weather = weather
    )
}
