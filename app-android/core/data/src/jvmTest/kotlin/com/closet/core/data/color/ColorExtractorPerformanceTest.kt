package com.closet.core.data.color

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Smoke guard (not a benchmark): a 512x512 extraction stays well under half a second on the JVM. */
class ColorExtractorPerformanceTest {

    @Test
    fun `large image stays fast`() {
        val extractor = ColorExtractor(SeedShadesFixture.shades)
        val random = Random(7)
        val pixels = IntArray(512 * 512) { random.nextInt() or -0x1000000 }
        extractor.extract(pixels, 512, 512) // warm-up

        var mark = TimeSource.Monotonic.markNow()
        val result = extractor.extract(pixels, 512, 512)
        var elapsed = mark.elapsedNow()
        assertTrue(result.size <= ColorExtractor.MAX_COLORS, "$result")

        if (elapsed >= 500.milliseconds) {
            // Confirm an over-limit sample with a fresh repeat to guard against JVM JIT warmup on cold runners
            mark = TimeSource.Monotonic.markNow()
            extractor.extract(pixels, 512, 512)
            elapsed = mark.elapsedNow()
        }
        assertTrue(elapsed < 500.milliseconds, "extract took $elapsed")
    }
}
