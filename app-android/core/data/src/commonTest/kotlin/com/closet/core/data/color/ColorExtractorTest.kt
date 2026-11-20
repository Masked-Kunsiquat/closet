package com.closet.core.data.color

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ColorExtractorTest {
    private val extractor = ColorExtractor(SeededShades.shades)
    private val namer = ColorNamer(SeededShades.shades)

    private fun id(name: String): Long = SeededShades.idOf(name)

    private fun opaque(rgb: Int): Int = rgb or OPAQUE

    private fun image(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): IntArray =
        IntArray(width * height) { i -> pixel(i % width, i / width) }

    private fun lab(rgb: Int): Lab = Oklab.fromArgb(opaque(rgb))

    /** 100x100: left 30 columns transparent; of the 70 opaque columns, 49 Navy and 21 White. */
    private fun maskedTwoColour(): IntArray = image(100, 100) { x, _ ->
        when {
            x < 30 -> 0x00000000
            x < 79 -> opaque(0x1F2A44)
            else -> opaque(0xF4F4F2)
        }
    }

    /** 100x100: top half transparent; the 5000 opaque pixels split [firstShare] [first], the rest [second]. */
    private fun maskedSplit(first: Int, second: Int, firstShare: Double): IntArray {
        val firstCount = (5000 * firstShare).toInt()
        return image(100, 100) { x, y ->
            val i = (y - 50) * 100 + x
            when {
                y < 50 -> 0x00000000
                i < firstCount -> opaque(first)
                else -> opaque(second)
            }
        }
    }

    /** 100x100 [rgb] with deterministic per-pixel ±6 channel noise. */
    private fun noisy(rgb: Int, seed: Int): IntArray {
        val random = Random(seed)
        fun jitter(c: Int): Int = (c + random.nextInt(-6, 7)).coerceIn(0, 255)
        return image(100, 100) { _, _ ->
            opaque((jitter((rgb shr 16) and 0xFF) shl 16) or (jitter((rgb shr 8) and 0xFF) shl 8) or jitter(rgb and 0xFF))
        }
    }

    /** `hexFromLinear` of the mean `linearRgb` of two equally weighted colours. */
    private fun linearMeanHex(a: Int, b: Int): String {
        val la = Oklab.linearRgb(a)
        val lb = Oklab.linearRgb(b)
        return Oklab.hexFromLinear((la[0] + lb[0]) / 2, (la[1] + lb[1]) / 2, (la[2] + lb[2]) / 2)
    }

    /** Per-byte mean in gamma (sRGB) space, rounded half up. */
    private fun gammaMeanHex(a: Int, b: Int): String {
        fun mean(shift: Int): Int = (((a shr shift) and 0xFF) + ((b shr shift) and 0xFF) + 1) / 2
        fun two(v: Int): String = v.toString(16).uppercase().padStart(2, '0')
        return "#" + two(mean(16)) + two(mean(8)) + two(mean(0))
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

        assertEquals(2, result.size, "$result")
        assertEquals(id("Navy"), result[0].colorId)
        assertEquals(0.70, result[0].share, 0.05)
        assertEquals(id("White"), result[1].colorId)
        assertEquals(0.30, result[1].share, 0.05)
    }

    @Test
    fun `close clusters that name differently merge into one colour`() {
        // Navy 70% and Denim 30%: separate k-means clusters (k = 2), but within DELTA_E_MERGE.
        val navy = 0x2C3E63
        val denim = 0x2F4A6B
        assertTrue(Oklab.deltaE(lab(navy), lab(denim)) < ColorExtractor.DELTA_E_MERGE, "precondition: within merge distance")
        assertEquals(id("Navy"), namer.nearest(lab(navy)))
        assertEquals(id("Denim"), namer.nearest(lab(denim)))

        val result = extractor.extract(maskedSplit(navy, denim, 0.70), 100, 100)

        // Unmerged this would be Navy 0.70 + Denim 0.30; merged it is one Navy at the full share.
        assertEquals(listOf(id("Navy")), result.map { it.colorId }, "$result")
        assertEquals(1.0, result[0].share, 1e-9)
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
    fun `item within background distance falls back to the centre`() {
        // A Cream item on a near-white backdrop: every pixel is within BG_DELTA_E of the border
        // median, so background removal leaves nothing and the centre box (exactly the item) is used.
        val backdrop = 0xFAFAFA
        val cream = 0xFAF3E0
        assertTrue(Oklab.deltaE(lab(backdrop), lab(cream)) < ColorExtractor.BG_DELTA_E, "precondition: within background distance")
        assertEquals(id("White"), namer.nearest(lab(backdrop)))
        assertEquals(id("Cream"), namer.nearest(lab(cream)))

        val result = extractor.extract(
            image(100, 100) { x, y -> if (x in 20 until 80 && y in 20 until 80) opaque(cream) else opaque(backdrop) },
            100,
            100,
        )

        assertEquals(listOf(id("Cream")), result.map { it.colorId }, "$result")
        assertEquals(1.0, result[0].share, 1e-9)
    }

    @Test
    fun `small accent below the share floor is dropped`() {
        // 8% Yellow then 92% Royal Blue.
        val result = extractor.extract(maskedSplit(0xFFD300, 0x2E50A8, 0.08), 100, 100)

        assertEquals(listOf(id("Royal Blue")), result.map { it.colorId }, "$result")
        // Not renormalized to 1.0 after the accent is dropped.
        assertEquals(0.92, result[0].share, 0.02)
    }

    @Test
    fun `noisy fabric stays one colour`() {
        val result = extractor.extract(noisy(0xB3202A, seed = 42), 100, 100)

        assertEquals(listOf(id("Red")), result.map { it.colorId }, "$result")
    }

    @Test
    fun `fully transparent image returns nothing`() {
        assertEquals(emptyList(), extractor.extract(IntArray(64 * 64), 64, 64))
    }

    @Test
    fun `same input gives the same output`() {
        // 512x512 random colours: k-means++ seeding decides the clusters, so any unseeded or
        // shared randomness shows up as different shares or hexes.
        val random = Random(7)
        val pixels = IntArray(512 * 512) { random.nextInt() or OPAQUE }

        val first = extractor.extract(pixels, 512, 512)
        val second = extractor.extract(pixels, 512, 512)
        val fresh = ColorExtractor(SeededShades.shades).extract(pixels, 512, 512)

        assertTrue(first.isNotEmpty(), "noise should still yield colours")
        assertEquals(first, second)
        assertEquals(first, fresh)
    }

    @Test
    fun `hex is uppercase and linear averaged`() {
        val solid = extractor.extract(image(64, 64) { _, _ -> opaque(0xB3202A) }, 64, 64)
        assertEquals("#B3202A", solid.single().hex)

        // #FF0000 and #D62B2B are farther apart than DELTA_E_MERGE: two clusters, combined by name.
        val pure = 0xFF0000
        val brick = 0xD62B2B
        assertTrue(Oklab.deltaE(lab(pure), lab(brick)) >= ColorExtractor.DELTA_E_MERGE)
        val combined = extractor.extract(maskedSplit(pure, brick, 0.5), 100, 100)
        assertEquals(listOf(id("Red")), combined.map { it.colorId }, "$combined")
        assertEquals(linearMeanHex(pure, brick), combined[0].hex)
        assertNotEquals(gammaMeanHex(pure, brick), combined[0].hex)

        // #B3202A and #C41E3A are within DELTA_E_MERGE: one merged cluster, still linear averaged.
        val red = 0xB3202A
        val crimson = 0xC41E3A
        assertTrue(Oklab.deltaE(lab(red), lab(crimson)) < ColorExtractor.DELTA_E_MERGE)
        val merged = extractor.extract(maskedSplit(red, crimson, 0.5), 100, 100)
        assertEquals(listOf(id("Red")), merged.map { it.colorId }, "$merged")
        assertEquals(linearMeanHex(red, crimson), merged[0].hex)
        assertNotEquals(gammaMeanHex(red, crimson), merged[0].hex)

        (solid + combined + merged).forEach { assertTrue(Regex("#[0-9A-F]{6}").matches(it.hex), it.hex) }
    }

    @Test
    fun `fine stripes are sampled without aliasing`() {
        // 200x200 with a 4-px transparent border (mask path): 192x192 = 36,864 item pixels, so the
        // sampler thins by 4. 2-px vertical stripes repeat every 4 px, so a fixed-phase stride of 4
        // would only ever see one stripe colour.
        val red = 0xB3202A
        val navy = 0x1F2A44
        val pixels = image(200, 200) { x, y ->
            when {
                x < 4 || x >= 196 || y < 4 || y >= 196 -> 0x00000000
                (x / 2) % 2 == 0 -> opaque(red)
                else -> opaque(navy)
            }
        }

        val result = extractor.extract(pixels, 200, 200)

        assertEquals(setOf(id("Red"), id("Navy")), result.map { it.colorId }.toSet(), "$result")
        result.forEach { assertEquals(0.5, it.share, 0.05, "$result") }
    }

    @Test
    fun `size mismatch is rejected`() {
        assertFailsWith<IllegalArgumentException> { extractor.extract(IntArray(10), 4, 4) }
    }

    @Test
    fun `calibration overrides out of range are rejected`() {
        val shades = SeededShades.shades
        assertFailsWith<IllegalArgumentException> { ColorExtractor(shades, deltaEMerge = -0.01, minShare = 0.15) }
        assertFailsWith<IllegalArgumentException> { ColorExtractor(shades, deltaEMerge = Double.NaN, minShare = 0.15) }
        assertFailsWith<IllegalArgumentException> { ColorExtractor(shades, deltaEMerge = 0.07, minShare = -0.1) }
        assertFailsWith<IllegalArgumentException> { ColorExtractor(shades, deltaEMerge = 0.07, minShare = 1.5) }
        assertFailsWith<IllegalArgumentException> { ColorExtractor(shades, deltaEMerge = 0.07, minShare = Double.NaN) }
        // The bounds themselves are allowed.
        ColorExtractor(shades, deltaEMerge = 0.0, minShare = 0.0)
        ColorExtractor(shades, deltaEMerge = 0.07, minShare = 1.0)
    }

    @Test
    fun `no reference shades gives nothing`() {
        val result = ColorExtractor(emptyList()).extract(image(64, 64) { _, _ -> opaque(0xB3202A) }, 64, 64)

        assertEquals(emptyList(), result)
    }

    private companion object {
        const val OPAQUE: Int = -0x1000000 // 0xFF000000
    }
}
