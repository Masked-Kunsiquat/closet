package com.closet.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * Pins the stored timestamp text to what `java.time.Instant.toString()` wrote before the
 * move to `kotlin.time.Instant`, so existing rows and backups keep parsing and new rows
 * keep the same layout.
 */
class ConvertersCompatTest {

    private val converters = Converters()

    @Test
    fun kotlinToStringMatchesJavaToString() {
        val seconds = listOf(0L, 1_763_000_000L)
        val nanos = listOf(0, 282_000_000, 282_100_000, 123_456_789)
        for (s in seconds) {
            for (n in nanos) {
                val expected = java.time.Instant.ofEpochSecond(s, n.toLong()).toString()
                val actual = converters.dateToTimestamp(Instant.fromEpochSeconds(s, n))
                assertEquals(expected, actual, "epochSeconds=$s nanos=$n")
            }
        }
    }

    @Test
    fun parsesStringsWrittenByJavaTime() {
        val inputs = listOf(
            "2026-11-15T03:50:25.282Z",
            "2026-11-15T03:50:25Z",
            "2026-11-15T03:50:25.123456789Z",
        )
        for (text in inputs) {
            val java = java.time.Instant.parse(text)
            val parsed = assertNotNull(converters.fromTimestamp(text), text)
            assertEquals(java.epochSecond, parsed.epochSeconds, text)
            assertEquals(java.nano, parsed.nanosecondsOfSecond, text)
        }
    }

    @Test
    fun localDateIsoTextUnchanged() {
        assertEquals("2026-04-04", kotlinx.datetime.LocalDate(2026, 4, 4).toString())
    }
}
