package com.closet.core.data.color

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ColorExtractorTest {
    private val extractor = ColorExtractor(SeedShadesFixture.shades)

    private fun id(name: String): Long = SeedShadesFixture.idOf(name)

    private fun opaque(rgb: Int): Int = rgb or OPAQUE

    private fun image(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): IntArray =
        IntArray(width * height) { i -> pixel(i % width, i / width) }

    /** 100x100: left 30 columns transparent; of the 70 opaque columns, 49 Navy and 21 White. */
    private fun maskedTwoColour(): IntArray = image(100, 100) { x, _ ->
        when {
            x < 30 -> 0x00000000
            x < 79 -> opaque(0x1F2A44)
            else -> opaque(0xF4F4F2)
        }
    }

    @Test
    fun `solid image returns its colour`() {
        val result = extractor.extract(image(64, 64) { _, _ -> opaque(0xB3202A) }, 64, 64)

        assertEquals(1, result.size, "$result")
        assertEquals(id("Red"), result[0].colorId)
        assertTrue(result[0].share >= 0.99, "share ${result[0].share}")
    }

    @Test
    fun `masked two colour item gives shares`() {
        val result = extractor.extract(maskedTwoColour(), 100, 100)
        println("ColorExtractor masked two colour: $result")

        assertEquals(2, result.size, "$result")
        assertEquals(id("Navy"), result[0].colorId)
        assertEquals(0.70, result[0].share, 0.05)
        assertEquals(id("White"), result[1].colorId)
        assertEquals(0.30, result[1].share, 0.05)
    }

    @Test
    fun `product photo drops a uniform white background`() {
        val result = extractor.extract(
            image(100, 100) { x, y ->
                if (x in 30 until 70 && y in 30 until 70) opaque(0xE8742A) else opaque(0xFFFFFF)
            },
            100,
            100,
        )

        assertEquals(listOf(id("Orange")), result.map { it.colorId }, "$result")
    }

    @Test
    fun `white item on white backdrop falls back to the centre`() {
        val result = extractor.extract(
            image(100, 100) { x, y ->
                if (x in 20 until 80 && y in 20 until 80) opaque(0xF4F4F2) else opaque(0xFAFAFA)
            },
            100,
            100,
        )

        assertEquals(listOf(id("White")), result.map { it.colorId }, "$result")
    }

    @Test
    fun `small accent below the share floor is dropped`() {
        // Top half transparent; the 5000 opaque pixels are 400 Yellow (8%) then 4600 Royal Blue (92%).
        val result = extractor.extract(
            image(100, 100) { x, y ->
                val i = (y - 50) * 100 + x
                when {
                    y < 50 -> 0x00000000
                    i < 400 -> opaque(0xFFD300)
                    else -> opaque(0x2E50A8)
                }
            },
            100,
            100,
        )
        println("ColorExtractor small accent: $result")

        assertEquals(listOf(id("Royal Blue")), result.map { it.colorId }, "$result")
    }

    @Test
    fun `noisy fabric stays one colour`() {
        val random = Random(42)
        fun jitter(c: Int): Int = (c + random.nextInt(-6, 7)).coerceIn(0, 255)
        val result = extractor.extract(
            image(100, 100) { _, _ -> opaque((jitter(0xB3) shl 16) or (jitter(0x20) shl 8) or jitter(0x2A)) },
            100,
            100,
        )

        assertEquals(listOf(id("Red")), result.map { it.colorId }, "$result")
    }

    @Test
    fun `fully transparent image returns nothing`() {
        assertEquals(emptyList(), extractor.extract(IntArray(64 * 64), 64, 64))
    }

    @Test
    fun `same input gives the same output`() {
        val first = extractor.extract(maskedTwoColour(), 100, 100)
        val second = ColorExtractor(SeedShadesFixture.shades).extract(maskedTwoColour(), 100, 100)

        assertEquals(first, second)
    }

    @Test
    fun `hex is uppercase and linear averaged`() {
        val solid = extractor.extract(image(64, 64) { _, _ -> opaque(0xB3202A) }, 64, 64)
        assertEquals("#B3202A", solid.single().hex)

        // 10 px transparent frame around an 80x80 one-pixel checkerboard.
        val checker = extractor.extract(
            image(100, 100) { x, y ->
                when {
                    x < 10 || x >= 90 || y < 10 || y >= 90 -> 0x00000000
                    (x + y) % 2 == 0 -> opaque(0x000000)
                    else -> opaque(0xFFFFFF)
                }
            },
            100,
            100,
        )

        assertEquals(setOf(id("Black"), id("White")), checker.map { it.colorId }.toSet(), "$checker")
        assertEquals(setOf("#000000", "#FFFFFF"), checker.map { it.hex }.toSet())
        checker.forEach { assertTrue(Regex("#[0-9A-F]{6}").matches(it.hex), it.hex) }
    }

    @Test
    fun `large image stays fast`() {
        val random = Random(7)
        val pixels = IntArray(512 * 512) { random.nextInt() or OPAQUE }
        extractor.extract(pixels, 512, 512) // warm-up

        val mark = TimeSource.Monotonic.markNow()
        val result = extractor.extract(pixels, 512, 512)
        val elapsed = mark.elapsedNow()

        println("ColorExtractor 512x512 noise: $elapsed")
        assertTrue(result.size <= 3, "$result")
        assertTrue(elapsed.inWholeMilliseconds < 500, "took $elapsed")
    }

    private companion object {
        const val OPAQUE: Int = -0x1000000 // 0xFF000000
    }
}
