package com.closet.features.stats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatPeriodTest {

    private val today = LocalDate(2026, 3, 15)

    @Test
    fun allTimeToFromDateReturnsNull() {
        assertNull(StatPeriod.ALL_TIME.toFromDate(today))
    }

    @Test
    fun last30ToFromDateReturnsDate30DaysAgo() {
        assertEquals("2026-02-13", StatPeriod.LAST_30.toFromDate(today))
    }

    @Test
    fun last90ToFromDateReturnsDate90DaysAgo() {
        assertEquals("2025-12-15", StatPeriod.LAST_90.toFromDate(today))
    }

    @Test
    fun thisYearToFromDateReturnsJanuaryFirstOfTheCurrentYear() {
        assertEquals("2026-01-01", StatPeriod.THIS_YEAR.toFromDate(today))
    }

    @Test
    fun last30ToFromDateCrossesLeapDay() {
        assertEquals("2024-01-31", StatPeriod.LAST_30.toFromDate(LocalDate(2024, 3, 1)))
    }

    @Test
    fun allNonAllTimePeriodsProduceYyyyMmDdFormattedDates() {
        val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")
        listOf(StatPeriod.LAST_30, StatPeriod.LAST_90, StatPeriod.THIS_YEAR).forEach { period ->
            val date = period.toFromDate()!!
            assertTrue(datePattern.matches(date), "$period produced malformed date: $date")
        }
    }

    @Test
    fun last30DateIsStrictlyAfterLast90Date() {
        val last30 = LocalDate.parse(StatPeriod.LAST_30.toFromDate()!!)
        val last90 = LocalDate.parse(StatPeriod.LAST_90.toFromDate()!!)
        assertTrue(last30 > last90)
    }

    @Test
    fun thisYearToFromDateIsAlwaysJanuaryFirstRegardlessOfCurrentDay() {
        val thisYear = LocalDate.parse(StatPeriod.THIS_YEAR.toFromDate()!!)
        assertEquals(1, thisYear.month.number)
        assertEquals(1, thisYear.day)
    }
}
