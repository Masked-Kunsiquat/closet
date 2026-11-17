package com.closet.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DateLabelsJvmTest {
    private lateinit var original: Locale

    @BeforeTest fun setUp() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest fun tearDown() = Locale.setDefault(original)

    @Test fun usLabels() {
        assertEquals("Sunday, March 15", formatDayMonthLong(LocalDate(2026, 3, 15)))
        assertEquals("March 15", formatMonthDay(LocalDate(2026, 3, 15)))
        assertEquals("March 2026", formatMonthYear(YearMonth(2026, 3)))
        assertEquals("Mon 16", formatWeekdayDay(LocalDate(2026, 3, 16)))
        assertEquals("M", weekdayNarrow(DayOfWeek.MONDAY))
    }

    @Test fun franceLabelsAreLocalized() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("mars 2026", formatMonthYear(YearMonth(2026, 3)))
        assertEquals("dimanche, mars 15", formatDayMonthLong(LocalDate(2026, 3, 15)))
    }
}
