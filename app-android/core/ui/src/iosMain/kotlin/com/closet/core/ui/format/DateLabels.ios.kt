package com.closet.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

private fun format(year: Int, month: Int, day: Int, configure: NSDateFormatter.() -> Unit): String {
    // kotlinx dates are proleptic Gregorian; currentCalendar may be Buddhist/Japanese/Islamic.
    val gregorian = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    val components = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
        this.hour = 12
    }
    val date = gregorian.dateFromComponents(components)
        ?: error("No Gregorian date for $year-$month-$day")
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.calendar = gregorian
    formatter.configure()
    return formatter.stringFromDate(date)
}

actual fun formatDayMonthLong(date: LocalDate): String =
    format(date.year, date.month.ordinal + 1, date.day) { dateFormat = "EEEE, MMMM d" }

actual fun formatDateLong(date: LocalDate): String =
    format(date.year, date.month.ordinal + 1, date.day) { dateStyle = NSDateFormatterLongStyle }

actual fun formatMonthDay(date: LocalDate): String =
    format(date.year, date.month.ordinal + 1, date.day) { dateFormat = "MMMM d" }

actual fun formatMonthYear(yearMonth: YearMonth): String =
    format(yearMonth.year, yearMonth.month.ordinal + 1, 1) { dateFormat = "MMMM yyyy" }

actual fun formatWeekdayDay(date: LocalDate): String =
    format(date.year, date.month.ordinal + 1, date.day) { dateFormat = "EEE d" }

actual fun weekdayNarrow(day: DayOfWeek): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    // veryShortStandaloneWeekdaySymbols is Sunday-first; DayOfWeek.ordinal is Monday-first.
    val symbols = formatter.veryShortStandaloneWeekdaySymbols
    return symbols[(day.ordinal + 1) % 7] as String
}
