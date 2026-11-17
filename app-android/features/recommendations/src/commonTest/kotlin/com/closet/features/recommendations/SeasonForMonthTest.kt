package com.closet.features.recommendations

import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonForMonthTest {
    @Test fun mapsEveryMonthToItsSeason() {
        val expected = mapOf(
            Month.MARCH to "Spring", Month.APRIL to "Spring", Month.MAY to "Spring",
            Month.JUNE to "Summer", Month.JULY to "Summer", Month.AUGUST to "Summer",
            Month.SEPTEMBER to "Fall", Month.OCTOBER to "Fall", Month.NOVEMBER to "Fall",
            Month.DECEMBER to "Winter", Month.JANUARY to "Winter", Month.FEBRUARY to "Winter",
        )
        assertEquals(12, expected.size)
        expected.forEach { (month, season) -> assertEquals(season, seasonFor(month), "month=$month") }
    }
}
