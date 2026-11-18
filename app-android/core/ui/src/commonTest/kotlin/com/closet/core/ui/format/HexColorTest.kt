package com.closet.core.ui.format

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HexColorTest {

    private fun assertColorClose(expected: Color, actual: Color?) {
        assertNotNull(actual)
        val tolerance = 0.002f
        assertTrue(abs(expected.red - actual.red) <= tolerance, "red: expected $expected, was $actual")
        assertTrue(abs(expected.green - actual.green) <= tolerance, "green: expected $expected, was $actual")
        assertTrue(abs(expected.blue - actual.blue) <= tolerance, "blue: expected $expected, was $actual")
        assertTrue(abs(expected.alpha - actual.alpha) <= tolerance, "alpha: expected $expected, was $actual")
    }

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
}
