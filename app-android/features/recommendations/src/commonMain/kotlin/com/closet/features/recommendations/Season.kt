package com.closet.features.recommendations

import kotlinx.datetime.Month

/**
 * Northern-hemisphere calendar season for [month], as a name matching the seeded values in the
 * `seasons` table (Spring / Summer / Autumn / Winter).
 */
internal fun seasonFor(month: Month): String = when (month) {
    Month.MARCH, Month.APRIL, Month.MAY -> "Spring"
    Month.JUNE, Month.JULY, Month.AUGUST -> "Summer"
    Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "Autumn"
    else -> "Winter"
}
