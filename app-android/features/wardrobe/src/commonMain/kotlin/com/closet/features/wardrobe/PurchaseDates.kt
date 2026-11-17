package com.closet.features.wardrobe

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The Material 3 date picker speaks UTC-midnight epoch millis. These map them to and from the ISO
 * [LocalDate] stored in the database, always in UTC so a negative-offset device time zone never
 * shifts the day.
 */
internal fun utcMillisToLocalDate(ms: Long): LocalDate =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date

internal fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
