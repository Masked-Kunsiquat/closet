package com.closet.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

private fun format(pattern: String, year: Int, month: Int, day: Int): String {
    val components = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
        this.hour = 12
    }
    val date = NSCalendar.currentCalendar.dateFromComponents(components) ?: NSDate()
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = pattern
    return formatter.stringFromDate(date)
}

actual fun formatDayMonthLong(date: LocalDate): String =
    format("EEEE, MMMM d", date.year, date.month.ordinal + 1, date.day)

actual fun formatMonthDay(date: LocalDate): String =
    format("MMMM d", date.year, date.month.ordinal + 1, date.day)

actual fun formatMonthYear(yearMonth: YearMonth): String =
    format("MMMM yyyy", yearMonth.year, yearMonth.month.ordinal + 1, 1)

actual fun formatWeekdayDay(date: LocalDate): String =
    format("EEE d", date.year, date.month.ordinal + 1, date.day)

actual fun weekdayNarrow(day: DayOfWeek): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    // veryShortStandaloneWeekdaySymbols is Sunday-first; DayOfWeek.ordinal is Monday-first.
    val symbols = formatter.veryShortStandaloneWeekdaySymbols
    return symbols[(day.ordinal + 1) % 7] as String
}
