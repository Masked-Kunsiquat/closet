package com.closet.platform.gms.chat

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MlKitTimeZoneTest {

    @Test
    fun fixedOffsetKeepsItsOffset() {
        // java.util.TimeZone.getTimeZone("+05:30") would fall back to GMT (offset 0).
        val zone = TimeZone.of("+05:30").toJavaUtilTimeZone()
        assertEquals((5 * 60 + 30) * 60 * 1000, zone.rawOffset)
    }

    @Test
    fun namedZoneKeepsItsDaylightSavingRules() {
        val zone = TimeZone.of("America/Los_Angeles").toJavaUtilTimeZone()
        assertEquals("America/Los_Angeles", zone.id)
        val january = zone.getOffset(1768462200000L) // 2026-01-15T07:30:00Z, PST
        val july = zone.getOffset(1784100600000L)    // 2026-07-15T07:30:00Z, PDT
        assertEquals(-8 * 60 * 60 * 1000, january)
        assertEquals(-7 * 60 * 60 * 1000, july)
        assertNotEquals(january, july)
    }
}
