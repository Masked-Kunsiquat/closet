package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.weather.WarmthLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectiveWeatherTest {

    private val neutral = WeatherFitPrior.NEUTRAL_SUITABILITY_PCT

    private fun temps(p10: Double, logs: Int) =
        ItemTempPercentiles(clothingItemId = 1, p10TempLow = p10, p90TempHigh = p10 + 10.0, logCount = logs)

    private fun rain(pct: Double, logs: Int) =
        ItemRainSuitability(clothingItemId = 1, rainPct = pct, rainLogCount = logs)

    @Test
    fun `history raises warmth by at most one level`() {
        // LIGHT's lower bound is 6 degrees, so a p10 of -20 is far below it, yet the level moves one step.
        assertEquals(WarmthLevel.MEDIUM, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, temps(-20.0, 5)))
        assertEquals(WarmthLevel.WARM, EffectiveWeather.warmth(WarmthLevel.MEDIUM, null, temps(-30.0, 40)))
        // Fewer than 5 logs carry no signal.
        assertEquals(WarmthLevel.LIGHT, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, temps(-20.0, 4)))
        // No history at all.
        assertEquals(WarmthLevel.LIGHT, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, null))
        // Exactly 3 degrees below LIGHT's bound is not more than 3 below it.
        assertEquals(WarmthLevel.LIGHT, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, temps(3.0, 10)))
        assertEquals(WarmthLevel.MEDIUM, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, temps(2.9, 10)))
    }

    @Test
    fun `history never promotes a base layer`() {
        assertEquals(WarmthLevel.NONE, EffectiveWeather.warmth(WarmthLevel.NONE, null, temps(-20.0, 10)))
    }

    @Test
    fun `history never lowers warmth`() {
        assertEquals(WarmthLevel.WARM, EffectiveWeather.warmth(WarmthLevel.WARM, null, temps(30.0, 50)))
        assertEquals(WarmthLevel.LIGHT, EffectiveWeather.warmth(WarmthLevel.LIGHT, null, temps(35.0, 50)))
        // HEAVY has no lower bound and nowhere higher to go.
        assertEquals(WarmthLevel.HEAVY, EffectiveWeather.warmth(WarmthLevel.HEAVY, null, temps(-40.0, 50)))
        assertEquals(WarmthLevel.HEAVY, EffectiveWeather.warmth(WarmthLevel.HEAVY, null, temps(30.0, 50)))
    }

    @Test
    fun `an override wins over derivation and history`() {
        assertEquals(
            WarmthLevel.NONE,
            EffectiveWeather.warmth(WarmthLevel.WARM, WarmthLevel.NONE.ordinal, temps(-40.0, 50)),
        )
        assertEquals(
            WarmthLevel.HEAVY,
            EffectiveWeather.warmth(WarmthLevel.NONE, WarmthLevel.HEAVY.ordinal, temps(30.0, 50)),
        )
        // An out-of-range stored value is no override: derivation and history apply.
        assertEquals(WarmthLevel.MEDIUM, EffectiveWeather.warmth(WarmthLevel.LIGHT, 9, temps(-20.0, 5)))
        assertEquals(WarmthLevel.NONE, EffectiveWeather.warmth(WarmthLevel.NONE, -1, null))

        assertFalse(EffectiveWeather.rainReady(derived = true, override = false, rain = rain(1.0, 50), prior = neutral))
        assertTrue(EffectiveWeather.rainReady(derived = false, override = true, rain = rain(0.0, 50), prior = neutral))
    }

    @Test
    fun `frequent rainy wear makes an item rain ready`() {
        // 10 logs give w = 10 over 15, so blended = two thirds of 0.8 plus one third of 0.5 = 0.7.
        assertTrue(EffectiveWeather.rainReady(derived = false, override = null, rain = rain(0.8, 10), prior = neutral))
        // Mostly dry wear stays not rain ready: blended is about 0.37.
        assertFalse(EffectiveWeather.rainReady(derived = false, override = null, rain = rain(0.3, 10), prior = neutral))
        // Fewer than 5 logs carry no signal even when every one was rainy.
        assertFalse(EffectiveWeather.rainReady(derived = false, override = null, rain = rain(1.0, 4), prior = neutral))
        assertFalse(EffectiveWeather.rainReady(derived = false, override = null, rain = null, prior = neutral))
        // History never turns a rain-ready item off.
        assertTrue(EffectiveWeather.rainReady(derived = true, override = null, rain = rain(0.0, 50), prior = neutral))
    }

    @Test
    fun `a rain unfriendly item resists becoming rain ready`() {
        // Sandals carry a 0.05 prior: blended = two thirds of 0.8 plus one third of 0.05 = 0.55.
        val sandals = item(id = 1, role = "Footwear", subcategoryName = "Sandals")
            .withEffectiveWeather(warmthOverride = null, rainReadyOverride = null, temps = null, rain = rain(0.8, 10))
        assertFalse(sandals.rainReady)
    }

    @Test
    fun `a protective type item becomes rain ready with less evidence`() {
        // A cotton Jacket is not derived rain ready but carries a 0.75 prior:
        // blended = two thirds of 0.6 plus one third of 0.75 = 0.65. A neutral prior would give about 0.57.
        val jacket = item(id = 1, role = "Outerwear", warmthLayer = "Outer", subcategoryName = "Jacket", materials = setOf("Cotton"))
            .withEffectiveWeather(warmthOverride = null, rainReadyOverride = null, temps = null, rain = rain(0.6, 10))
        assertTrue(jacket.rainReady)
    }
}
