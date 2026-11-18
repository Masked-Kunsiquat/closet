package com.closet.features.outfits

import kotlinx.datetime.LocalDate

/** Parses an ISO "YYYY-MM-DD" date, or returns null when it is malformed or not a real calendar date. */
internal fun parseIsoDateOrNull(s: String): LocalDate? =
    try {
        LocalDate.parse(s)
    } catch (_: IllegalArgumentException) {
        null
    }
