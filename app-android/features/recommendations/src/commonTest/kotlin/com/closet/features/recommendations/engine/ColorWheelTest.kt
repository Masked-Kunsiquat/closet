package com.closet.features.recommendations.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorWheelTest {

    @Test
    fun `anchor hues land exactly on their wheel positions`() {
        assertEquals(0.0, ColorWheel.warp(29.0), 1e-9)    // red
        assertEquals(60.0, ColorWheel.warp(71.0), 1e-9)   // orange
        assertEquals(120.0, ColorWheel.warp(110.0), 1e-9) // yellow
        assertEquals(180.0, ColorWheel.warp(143.0), 1e-9) // green
        assertEquals(240.0, ColorWheel.warp(266.0), 1e-9) // blue
        assertEquals(300.0, ColorWheel.warp(328.0), 1e-9) // purple
    }

    @Test
    fun `hues below red interpolate on the purple-to-red segment`() {
        // 300 + (360 - 328) / 61 * 60
        assertEquals(331.475, ColorWheel.warp(0.0), 1e-3)
    }

    @Test
    fun `warp is continuous across the wrap`() {
        assertEquals(360.0, ColorWheel.warp(28.999), 0.01)
        assertEquals(300.0, ColorWheel.warp(328.001), 0.01)
    }

    @Test
    fun `warp stays in 0 to 360`() {
        var h = 0.0
        while (h < 360.0) {
            val w = ColorWheel.warp(h)
            assertTrue(w >= 0.0 && w < 360.0, "warp($h) = $w")
            h += 0.25
        }
    }

    @Test
    fun `gap folds the angular distance to 0 to 180`() {
        assertEquals(20.0, ColorWheel.gap(350.0, 10.0), 1e-9)
        assertEquals(20.0, ColorWheel.gap(10.0, 350.0), 1e-9)
        assertEquals(180.0, ColorWheel.gap(0.0, 180.0), 1e-9)
        assertEquals(0.0, ColorWheel.gap(42.0, 42.0), 1e-9)
    }
}
