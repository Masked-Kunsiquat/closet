package com.closet.features.outfits

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Today's date in the device's time zone. */
internal fun todayLocal(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Parses an ISO "YYYY-MM-DD" date, or returns null when it is malformed or not a real calendar date. */
internal fun parseIsoDateOrNull(s: String): LocalDate? =
    try {
        LocalDate.parse(s)
    } catch (_: IllegalArgumentException) {
        null
    }
