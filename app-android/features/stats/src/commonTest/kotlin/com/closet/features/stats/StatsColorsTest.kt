package com.closet.features.stats

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsColorsTest {

    /** Per-channel comparison: the saturation ramp is float arithmetic, so `==` is too strict. */
    private fun assertColorClose(expected: Color, actual: Color?) {
        assertNotNull(actual)
        val tolerance = 0.002f
        assertTrue(abs(expected.red - actual.red) <= tolerance, "red: expected $expected, was $actual")
        assertTrue(abs(expected.green - actual.green) <= tolerance, "green: expected $expected, was $actual")
        assertTrue(abs(expected.blue - actual.blue) <= tolerance, "blue: expected $expected, was $actual")
        assertTrue(abs(expected.alpha - actual.alpha) <= tolerance, "alpha: expected $expected, was $actual")
    }

    // ─── parseHexColor ────────────────────────────────────────────────────────

    @Test
    fun parseHexColorParsesUppercaseRrggbb() {
        assertColorClose(Color(0xFFFF0000), parseHexColor("#FF0000"))
    }

    @Test
    fun parseHexColorParsesLowercaseRrggbb() {
        assertColorClose(Color(0xFFF5F5DC), parseHexColor("#f5f5dc"))
    }

    @Test
    fun parseHexColorParsesAarrggbb() {
        assertColorClose(Color(0x80000080), parseHexColor("#80000080"))
    }

    @Test
    fun parseHexColorReturnsNullForInvalidInput() {
        listOf(null, "", "FF0000", "#FFF", "#GG0000", "#FF00000").forEach { input ->
            assertNull(parseHexColor(input), "expected null for \"$input\"")
        }
    }

    // ─── subcategoryColors ────────────────────────────────────────────────────

    @Test
    fun subcategoryColorsSingleSegmentUsesMidSaturation() {
        assertColorClose(Color.hsv(210f, 0.65f, 0.90f), subcategoryColors(0, 1).single())
    }

    @Test
    fun subcategoryColorsRampsSaturationFromVividToPastel() {
        val colors = subcategoryColors(1, 3)
        assertEquals(3, colors.size)
        assertColorClose(Color.hsv(20f, 0.85f, 0.9f), colors[0])
        assertColorClose(Color.hsv(20f, 0.575f, 0.9f), colors[1])
        assertColorClose(Color.hsv(20f, 0.30f, 0.9f), colors[2])
    }

    @Test
    fun subcategoryColorsHueWrapsAroundAfterLastSlot() {
        assertColorClose(Color.hsv(210f, 0.65f, 0.90f), subcategoryColors(8, 1).single())
    }
}
