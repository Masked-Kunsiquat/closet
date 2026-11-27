package com.closet.features.recommendations.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The scoreboard's colour-harmony switch (#148): tests only; production always has harmony on. */
class HarmonySwitchTest {

    private val defaultEngine = OutfitRecommendationEngine()
    private val harmonyOn = OutfitRecommendationEngine.withColorHarmony(true)
    private val harmonyOff = OutfitRecommendationEngine.withColorHarmony(false)

    /** A complementary pair (× 1.05 with harmony on). */
    private val harmonious = listOf(
        item(1L, role = "Top").inColors("Red"),
        item(2L, role = "Bottom").inColors("Forest Green"),
    )

    /** The same two garments in a pair about 90 degrees apart (× 1.0 with harmony on). */
    private val plain = listOf(
        item(1L, role = "Top").inColors("Forest Green"),
        item(2L, role = "Bottom").inColors("Yellow"),
    )

    /** Same-family cohesion (× 1.10 with harmony on), with a pattern and a pair of shoes. */
    private val sameFamily = listOf(
        item(3L, role = "Top", isPatternSolid = false).inColors("Red"),
        item(4L, role = "Bottom").inColors("Burgundy"),
        item(5L, role = "Footwear").inColors("Navy"),
    )

    private val scores = mapOf(1L to 0.9, 2L to 1.0, 3L to 0.8, 4L to 1.05, 5L to 0.7)

    /** Some co-wear history, so the habit blend (0 < W < 1) is exercised too. */
    private val coWear = CoWearStats(
        pairCounts = mapOf((1L to 2L) to 3.0, (3L to 4L) to 2.0),
        itemCounts = mapOf(1L to 3.0, 2L to 3.0, 3L to 2.0, 4L to 4.0, 5L to 1.0),
        total = 10.0,
    )

    private fun OutfitRecommendationEngine.score(items: List<EngineItem>, history: CoWearStats) =
        scoreCombo(
            items = items,
            itemScores = scores,
            weather = weather(tempLow = 2.0, tempHigh = 8.0),
            hasFootwearInPool = true,
            coWear = history,
        )

    @Test
    fun `default engine scores equal the harmony on engine`() {
        for (items in listOf(harmonious, plain, sameFamily)) {
            for (history in listOf(CoWearStats.EMPTY, coWear)) {
                assertEquals(defaultEngine.score(items, history), harmonyOn.score(items, history))
            }
        }
    }

    @Test
    fun `harmony off scores ignore colour`() {
        assertNotEquals(harmonyOn.score(harmonious, CoWearStats.EMPTY), harmonyOn.score(plain, CoWearStats.EMPTY))
        assertEquals(harmonyOff.score(harmonious, CoWearStats.EMPTY), harmonyOff.score(plain, CoWearStats.EMPTY))
        assertEquals(harmonyOff.score(harmonious, coWear), harmonyOff.score(plain, coWear))
        // Off is exactly × 1.0: the 1.0 pair scores the same either way.
        assertEquals(harmonyOn.score(plain, CoWearStats.EMPTY), harmonyOff.score(plain, CoWearStats.EMPTY))
    }
}
