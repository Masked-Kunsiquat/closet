package com.closet.core.data.color

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OklabTest {
    private fun near(expected: Double, actual: Double, tol: Double) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected but was $actual")

    @Test
    fun `white maps to L 1`() {
        val lab = Oklab.fromHex("#FFFFFF")
        near(1.0, lab.l, 1e-4)
        near(0.0, lab.a, 1e-4)
        near(0.0, lab.b, 1e-4)
    }

    @Test
    fun `red maps to the published vector`() {
        val lab = Oklab.fromHex("#FF0000")
        near(0.6280, lab.l, 1e-4)
        near(0.2249, lab.a, 1e-4)
        near(0.1258, lab.b, 1e-4)
        near(0.2577, Oklab.chroma(lab), 1e-4)
        near(29.2, Oklab.hueDegrees(lab), 0.1)
    }

    @Test
    fun `srgb round trip`() {
        for (v in 0..255) {
            val back = Oklab.linearToSrgb(Oklab.srgbToLinear(v / 255.0)) * 255
            assertTrue(abs(back - v) < 0.5, "byte $v round-tripped to $back")
        }
    }

    @Test
    fun `linear mean of black and white`() {
        val x = Oklab.linearRgb(0xFF000000.toInt())
        val y = Oklab.linearRgb(0xFFFFFFFF.toInt())
        val hex = Oklab.hexFromLinear((x[0] + y[0]) / 2, (x[1] + y[1]) / 2, (x[2] + y[2]) / 2)
        assertEquals("#BCBCBC", hex)
    }

    @Test
    fun `rejects malformed hex`() {
        assertFailsWith<IllegalArgumentException> { Oklab.fromHex("#FFF") }
        assertFailsWith<IllegalArgumentException> { Oklab.fromHex("FF0000") }
        assertFailsWith<IllegalArgumentException> { Oklab.fromHex("#GG0000") }
    }

    @Test
    fun `hue stays in range and hexFromLinear clamps`() {
        val h = Oklab.hueDegrees(Oklab.fromHex("#0000FF"))
        assertTrue(h >= 0.0 && h < 360.0)
        assertEquals("#FFFFFF", Oklab.hexFromLinear(2.0, 1.5, 1.0))
        assertEquals("#000000", Oklab.hexFromLinear(-1.0, -0.5, 0.0))
    }
}
