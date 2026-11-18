package com.closet.features.chat

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class RegexDateParserTest {

    // Pinned clock date: a yearless date must take its year from here, not the machine clock.
    private val today = LocalDate(2031, 6, 15)
    private val currentYear = today.year

    // ─── ISO dates ────────────────────────────────────────────────────────────

    @Test
    fun `ISO date string is returned as-is`() {
        assertEquals("2026-04-04", regexParseDate("2026-04-04", today))
    }

    @Test
    fun `ISO date embedded in a sentence is extracted`() {
        assertEquals("2026-04-04", regexParseDate("what did i wear on 2026-04-04 last year", today))
    }

    @Test
    fun `ISO pattern is a regex match not a validator - invalid calendar date is returned as-is`() {
        // The ISO fast path returns the first \d{4}-\d{2}-\d{2} match without date validation.
        assertEquals("2026-13-01", regexParseDate("2026-13-01", today))
    }

    // ─── Month Day patterns ───────────────────────────────────────────────────

    @Test
    fun `full month name and day uses current year`() {
        val expected = "${currentYear.toString().padStart(4, '0')}-04-04"
        assertEquals(expected, regexParseDate("April 4", today))
    }

    @Test
    fun `ordinal suffix is stripped`() {
        val expected = "${currentYear.toString().padStart(4, '0')}-04-04"
        assertEquals(expected, regexParseDate("April 4th", today))
    }

    @Test
    fun `abbreviated month name is recognized`() {
        val expected = "${currentYear.toString().padStart(4, '0')}-04-04"
        assertEquals(expected, regexParseDate("Apr 4", today))
    }

    @Test
    fun `explicit year overrides current year`() {
        assertEquals("2025-04-04", regexParseDate("april 4, 2025", today))
    }

    @Test
    fun `input is case-insensitive`() {
        val expected = "${currentYear.toString().padStart(4, '0')}-04-04"
        assertEquals(expected, regexParseDate("APRIL 4", today))
    }

    @Test
    fun `all full month names are recognized`() {
        val months = listOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        )
        months.forEach { (name, num) ->
            val expected = "${currentYear.toString().padStart(4, '0')}-${num.toString().padStart(2, '0')}-01"
            assertEquals(expected, regexParseDate("$name 1", today), "Failed for: $name")
        }
    }

    @Test
    fun `all abbreviated month names are recognized`() {
        // "may" has no distinct abbreviation — it is its own full form.
        val abbrevs = listOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9,
            "oct" to 10, "nov" to 11, "dec" to 12,
        )
        abbrevs.forEach { (abbr, num) ->
            val expected = "${currentYear.toString().padStart(4, '0')}-${num.toString().padStart(2, '0')}-01"
            assertEquals(expected, regexParseDate("$abbr 1", today), "Failed for: $abbr")
        }
    }

    @Test
    fun `invalid day for month returns null`() {
        // April has 30 days; day 31 throws in LocalDate-of and the catch returns null.
        assertNull(regexParseDate("April 31", today))
    }

    // ─── Unhandled patterns (must return null) ────────────────────────────────

    @Test
    fun `yesterday returns null`() {
        assertNull(regexParseDate("yesterday", today))
    }

    @Test
    fun `last Monday returns null`() {
        assertNull(regexParseDate("last Monday", today))
    }

    @Test
    fun `3 days ago returns null`() {
        assertNull(regexParseDate("3 days ago", today))
    }

    @Test
    fun `blank string returns null`() {
        assertNull(regexParseDate("", today))
    }

    @Test
    fun `unrelated input returns null`() {
        assertNull(regexParseDate("wear count", today))
    }
}
