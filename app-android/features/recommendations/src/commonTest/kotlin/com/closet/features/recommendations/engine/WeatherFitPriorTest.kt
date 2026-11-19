package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherFitPriorTest {

    private val engine = OutfitRecommendationEngine()

    @Test
    fun `bare item without weather attributes returns null prior`() {
        val bare = item(id = 1, role = "Top")
        assertNull(WeatherFitPrior.forItem(bare))
    }

    @Test
    fun `zero logs - wool sweater scores below linen shirt on a hot day and above it on a cold day`() {
        val woolSweater = item(
            id = 1,
            role = "Top",
            subcategoryName = "Sweater",
            materials = setOf("Wool"),
        )
        val linenShirt = item(
            id = 2,
            role = "Top",
            subcategoryName = "Shirt",
            materials = setOf("Linen"),
        )

        val hotDay = engineInput(
            candidates = listOf(woolSweater, linenShirt),
            weather = weather(tempLow = 26.0, tempHigh = 32.0),
        )
        val coldDay = engineInput(
            candidates = listOf(woolSweater, linenShirt),
            weather = weather(tempLow = -4.0, tempHigh = 2.0),
        )

        val hotWoolScore = engine.itemScore(woolSweater, hotDay)
        val hotLinenScore = engine.itemScore(linenShirt, hotDay)
        assertTrue(
            hotWoolScore < hotLinenScore,
            "On a hot 26..32°C day, wool sweater ($hotWoolScore) must score below linen shirt ($hotLinenScore)"
        )
        assertEquals(0.55, hotWoolScore, 1e-6)
        assertEquals(1.0, hotLinenScore, 1e-6)

        val coldWoolScore = engine.itemScore(woolSweater, coldDay)
        val coldLinenScore = engine.itemScore(linenShirt, coldDay)
        assertTrue(
            coldWoolScore > coldLinenScore,
            "On a cold -4..2°C day, wool sweater ($coldWoolScore) must score above linen shirt ($coldLinenScore)"
        )
        assertEquals(1.0, coldWoolScore, 1e-6)
        assertEquals(0.55, coldLinenScore, 1e-6)
    }

    @Test
    fun `one sparse log at a single temperature is cushioned by the prior comfort band`() {
        // Wool sweater has prior band [-11.0, 10.0]°C (Sweater [-5, 16] + Wool -6).
        // Suppose it was logged once at 8..8°C (a single-point interval if uncushioned).
        // With Option A shrinkage (w = 1 / 6), effLow = (1/6)*8 + (5/6)*(-11) = -7.83°C,
        // effHigh = (1/6)*8 + (5/6)*10 = 9.67°C.
        // On a 0..4°C day, forecast is still well inside both prior and shrunk comfort band!
        val woolSweater = item(
            id = 1,
            role = "Top",
            subcategoryName = "Sweater",
            materials = setOf("Wool"),
        )
        val singleLogPercentiles = mapOf(
            1L to ItemTempPercentiles(
                clothingItemId = 1,
                p10TempLow = 8.0,
                p90TempHigh = 8.0,
                logCount = 1,
            )
        )
        val coldDay = engineInput(
            candidates = listOf(woolSweater),
            tempPercentiles = singleLogPercentiles,
            weather = weather(tempLow = 0.0, tempHigh = 4.0),
        )

        assertEquals(1.0, engine.itemScore(woolSweater, coldDay), 1e-6)
    }

    @Test
    fun `plenty of logs dominate the prior when history contradicts the estimate`() {
        // Wool sweater (prior [-11, 10]°C) worn 25 times in warm 18..26°C offices.
        val woolSweater = item(
            id = 1,
            role = "Top",
            subcategoryName = "Sweater",
            materials = setOf("Wool"),
        )
        val warmDay = weather(tempLow = 20.0, tempHigh = 24.0)

        val zeroLogsScore = engine.itemScore(
            woolSweater,
            engineInput(candidates = listOf(woolSweater), weather = warmDay),
        )
        val manyLogsScore = engine.itemScore(
            woolSweater,
            engineInput(
                candidates = listOf(woolSweater),
                tempPercentiles = mapOf(
                    1L to ItemTempPercentiles(
                        clothingItemId = 1,
                        p10TempLow = 18.0,
                        p90TempHigh = 26.0,
                        logCount = 25,
                    )
                ),
                weather = warmDay,
            ),
        )

        assertEquals(0.55, zeroLogsScore, 1e-6)
        // With n = 25, w = 25 / 30 = 0.8333. effLow = 0.8333*18 + 0.1667*(-11) = 13.17,
        // effHigh = 0.8333*26 + 0.1667*10 = 23.33 -> 20..24 overlaps [13.17, 23.33], so mPost = 1.0.
        // Blended score = (25/30)*1.0 + (5/30)*0.55 = 0.925 (near 1.0, far above 0.55).
        assertTrue(
            manyLogsScore > 0.90,
            "With 25 logs confirming warm-weather wear, learned score ($manyLogsScore) should dominate prior ($zeroLogsScore)"
        )
    }

    @Test
    fun `rain and wind priors penalize suede sandals and reward nylon jacket at zero logs`() {
        val suedeSandals = item(
            id = 1,
            role = "Footwear",
            subcategoryName = "Sandals",
            materials = setOf("Suede"),
        )
        val nylonJacket = item(
            id = 2,
            role = "Outerwear",
            warmthLayer = "Outer",
            subcategoryName = "Jacket",
            materials = setOf("Nylon"),
        )
        val chiffonSkirt = item(
            id = 3,
            role = "Bottom",
            subcategoryName = "Skirt",
            materials = setOf("Chiffon"),
        )

        val stormyInput = engineInput(
            candidates = listOf(suedeSandals, nylonJacket, chiffonSkirt),
            weather = weather(isRaining = true, isWindy = true),
        )

        // Suede sandals: rain prior unsuitable (0.60) × wind prior unsuitable (0.70) = 0.42
        assertEquals(0.42, engine.itemScore(suedeSandals, stormyInput), 1e-6)
        // Nylon jacket: rain suitable (1.0) × wind suitable (1.0) = 1.0
        assertEquals(1.0, engine.itemScore(nylonJacket, stormyInput), 1e-6)
        // Chiffon skirt: rain unsuitable (0.60) × wind unsuitable (0.70) = 0.42
        assertEquals(0.42, engine.itemScore(chiffonSkirt, stormyInput), 1e-6)

        // When suede sandals have 25 rainy logs (rainPct = 0.50), history overrides the rain prior
        val rainyVeteranInput = engineInput(
            candidates = listOf(suedeSandals),
            rainSuitability = mapOf(
                1L to ItemRainSuitability(clothingItemId = 1, rainPct = 0.50, rainLogCount = 25)
            ),
            windSuitability = mapOf(
                1L to ItemWindSuitability(clothingItemId = 1, windPct = 0.50, windLogCount = 25)
            ),
            weather = weather(isRaining = true, isWindy = true),
        )
        assertTrue(engine.itemScore(suedeSandals, rainyVeteranInput) > 0.85)
    }

    @Test
    fun `five logs inside empirical p10-p90 band are not penalized by shrunk bounds`() {
        // Wool sweater has prior band [-11.0, 10.0]°C.
        // With 5 logs in 18..26°C, w = 5 / 10 = 0.5.
        // If shrunk bounds were used at n = 5, effHigh would be 0.5*26 + 0.5*10 = 18.0°C,
        // so a 20..25°C forecast (inside the observed [18, 26]°C p10..p90 band!) would wrongly
        // fall above effHigh and take the 0.55 penalty on the empirical term.
        // Using the raw empirical [18, 26]°C band once n >= 5 gives mPost = 1.0 and
        // blended multiplier = 0.5 * 1.0 + 0.5 * 0.55 = 0.775.
        val woolSweater = item(
            id = 1,
            role = "Top",
            subcategoryName = "Sweater",
            materials = setOf("Wool"),
        )
        val input = engineInput(
            candidates = listOf(woolSweater),
            tempPercentiles = mapOf(
                1L to ItemTempPercentiles(
                    clothingItemId = 1,
                    p10TempLow = 18.0,
                    p90TempHigh = 26.0,
                    logCount = 5,
                )
            ),
            weather = weather(tempLow = 20.0, tempHigh = 25.0),
        )

        assertEquals(0.775, engine.itemScore(woolSweater, input), 1e-6)
    }
}
