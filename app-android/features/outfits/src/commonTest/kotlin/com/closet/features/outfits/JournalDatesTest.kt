package com.closet.features.outfits

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JournalDatesTest {
    @Test fun `invalid calendar date parses to null`() {
        assertNull(parseIsoDateOrNull("2026-02-30"))
        assertNull(parseIsoDateOrNull("not-a-date"))
    }

    @Test fun `valid iso date parses`() {
        assertEquals(LocalDate(2026, 3, 15), parseIsoDateOrNull("2026-03-15"))
    }

    @Test fun `month minus one crosses the year boundary`() {
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).minus(1, DateTimeUnit.MONTH))
    }
}
