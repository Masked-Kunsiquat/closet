package com.closet.features.recommendations

import kotlinx.datetime.Month

/**
 * Northern-hemisphere calendar season for [month], as a name matching the seeded values in the
 * `seasons` table (Spring / Summer / Fall / Winter; see `DatabaseSeeder.seedSeasons`). The
 * candidate query compares it with `seasons.name`, so it must match the seeded names exactly.
 */
internal fun seasonFor(month: Month): String = when (month) {
    Month.MARCH, Month.APRIL, Month.MAY -> "Spring"
    Month.JUNE, Month.JULY, Month.AUGUST -> "Summer"
    Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "Fall"
    else -> "Winter"
}
