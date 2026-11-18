package com.closet.core.data.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AppClockTest {
    private val instant = Instant.parse("2026-01-15T07:30:00Z")

    @Test fun todayUsesTheClockZoneNotUtc() {
        val clock = FixedAppClock(instant, TimeZone.of("America/Los_Angeles"))
        assertEquals(LocalDate(2026, 1, 14), clock.today())
    }

    @Test fun todayInUtcIsTheUtcDate() {
        assertEquals(LocalDate(2026, 1, 15), FixedAppClock(instant, TimeZone.UTC).today())
    }

    @Test fun fixedClockDefaultsToUtcAndReturnsItsInstant() {
        val clock = FixedAppClock(instant)
        assertEquals(instant, clock.now())
        assertEquals(TimeZone.UTC, clock.timeZone())
    }
}
