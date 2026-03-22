package com.closet.features.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatPeriodTest {

    @Test
    fun `ALL_TIME toFromDate returns null`() {
        assertNull(StatPeriod.ALL_TIME.toFromDate())
    }

    @Test
    fun `LAST_30 toFromDate returns date 30 days ago`() {
        val expected = LocalDate.now().minusDays(30).toString()
        assertEquals(expected, StatPeriod.LAST_30.toFromDate())
    }

    @Test
    fun `LAST_90 toFromDate returns date 90 days ago`() {
        val expected = LocalDate.now().minusDays(90).toString()
        assertEquals(expected, StatPeriod.LAST_90.toFromDate())
    }

    @Test
    fun `THIS_YEAR toFromDate returns January 1st of the current year`() {
        val expected = LocalDate.of(LocalDate.now().year, 1, 1).toString()
        assertEquals(expected, StatPeriod.THIS_YEAR.toFromDate())
    }

    @Test
    fun `all non-ALL_TIME periods produce YYYY-MM-DD formatted dates`() {
        val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")
        listOf(StatPeriod.LAST_30, StatPeriod.LAST_90, StatPeriod.THIS_YEAR).forEach { period ->
            val date = period.toFromDate()!!
            assertTrue("$period produced malformed date: $date", datePattern.matches(date))
        }
    }

    @Test
    fun `LAST_30 date is strictly before LAST_90 date`() {
        val last30 = LocalDate.parse(StatPeriod.LAST_30.toFromDate()!!)
        val last90 = LocalDate.parse(StatPeriod.LAST_90.toFromDate()!!)
        assertTrue(last30.isAfter(last90))
    }

    @Test
    fun `THIS_YEAR toFromDate is always January 1st regardless of current day`() {
        val thisYear = LocalDate.parse(StatPeriod.THIS_YEAR.toFromDate()!!)
        assertEquals(1, thisYear.monthValue)
        assertEquals(1, thisYear.dayOfMonth)
    }
}
