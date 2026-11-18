package com.closet.features.wardrobe

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PurchaseDatesTest {
    // 2026-04-04T00:00:00Z
    private val april4Millis = 1_775_260_800_000L

    @Test
    fun `utc millis to local date`() {
        assertEquals(LocalDate(2026, 4, 4), utcMillisToLocalDate(april4Millis))
    }

    @Test
    fun `local date to utc millis`() {
        assertEquals(april4Millis, localDateToUtcMillis(LocalDate(2026, 4, 4)))
    }

    @Test
    fun `epoch zero is 1970-01-01`() {
        assertEquals(LocalDate(1970, 1, 1), utcMillisToLocalDate(0L))
        assertEquals(0L, localDateToUtcMillis(LocalDate(1970, 1, 1)))
    }

    @Test
    fun `late-day utc millis stays on the same date`() {
        assertEquals(LocalDate(2026, 4, 4), utcMillisToLocalDate(april4Millis + 86_399_999L))
    }

    @Test
    fun `round trip`() {
        val d = LocalDate(2024, 2, 29)
        assertEquals(d, utcMillisToLocalDate(localDateToUtcMillis(d)))
    }
}
