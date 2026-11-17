package com.closet.core.ui.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaYearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Duplicated in DateLabels.jvm.kt: android and jvm share no intermediate source set.
actual fun formatDayMonthLong(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()).format(date.toJavaLocalDate())

actual fun formatMonthDay(date: LocalDate): String =
    DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()).format(date.toJavaLocalDate())

actual fun formatMonthYear(yearMonth: YearMonth): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()).format(yearMonth.toJavaYearMonth())

actual fun formatWeekdayDay(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()).format(date.toJavaLocalDate())

actual fun weekdayNarrow(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.ordinal + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault())
