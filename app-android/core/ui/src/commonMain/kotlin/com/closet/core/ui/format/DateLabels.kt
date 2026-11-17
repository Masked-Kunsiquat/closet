package com.closet.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** `EEEE, MMMM d` in the current locale, e.g. "Sunday, March 15". */
expect fun formatDayMonthLong(date: LocalDate): String

/** `MMMM d` in the current locale, e.g. "March 15". */
expect fun formatMonthDay(date: LocalDate): String

/** `MMMM yyyy` in the current locale, e.g. "March 2026". */
expect fun formatMonthYear(yearMonth: YearMonth): String

/** `EEE d` in the current locale, e.g. "Mon 16". */
expect fun formatWeekdayDay(date: LocalDate): String

/** The narrow weekday name in the current locale, e.g. "M". */
expect fun weekdayNarrow(day: DayOfWeek): String
