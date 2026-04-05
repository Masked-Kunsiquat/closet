package com.closet.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RegexDateParserTest {

    private val currentYear = LocalDate.now().year

    // ─── ISO dates ────────────────────────────────────────────────────────────

    @Test
    fun `ISO date string is returned as-is`() {
        assertEquals("2026-04-04", regexParseDate("2026-04-04"))
    }

    @Test
    fun `ISO date embedded in a sentence is extracted`() {
        assertEquals("2026-04-04", regexParseDate("what did i wear on 2026-04-04 last year"))
    }

    @Test
    fun `ISO pattern is a regex match not a validator - invalid calendar date is returned as-is`() {
        // The ISO fast path returns the first \d{4}-\d{2}-\d{2} match without date validation.
        assertEquals("2026-13-01", regexParseDate("2026-13-01"))
    }

    // ─── Month Day patterns ───────────────────────────────────────────────────

    @Test
    fun `full month name and day uses current year`() {
        val expected = "%04d-04-04".format(currentYear)
        assertEquals(expected, regexParseDate("April 4"))
    }

    @Test
    fun `ordinal suffix is stripped`() {
        val expected = "%04d-04-04".format(currentYear)
        assertEquals(expected, regexParseDate("April 4th"))
    }

    @Test
    fun `abbreviated month name is recognized`() {
        val expected = "%04d-04-04".format(currentYear)
        assertEquals(expected, regexParseDate("Apr 4"))
    }

    @Test
    fun `explicit year overrides current year`() {
        assertEquals("2025-04-04", regexParseDate("april 4, 2025"))
    }

    @Test
    fun `input is case-insensitive`() {
        val expected = "%04d-04-04".format(currentYear)
        assertEquals(expected, regexParseDate("APRIL 4"))
    }

    @Test
    fun `all full month names are recognized`() {
        val months = listOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        )
        months.forEach { (name, num) ->
            val expected = "%04d-%02d-01".format(currentYear, num)
            assertEquals("Failed for: $name", expected, regexParseDate("$name 1"))
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
            val expected = "%04d-%02d-01".format(currentYear, num)
            assertEquals("Failed for: $abbr", expected, regexParseDate("$abbr 1"))
        }
    }

    @Test
    fun `invalid day for month returns null`() {
        // April has 30 days; day 31 throws in LocalDate-of and the catch returns null.
        assertNull(regexParseDate("April 31"))
    }

    // ─── Unhandled patterns (must return null) ────────────────────────────────

    @Test
    fun `yesterday returns null`() {
        assertNull(regexParseDate("yesterday"))
    }

    @Test
    fun `last Monday returns null`() {
        assertNull(regexParseDate("last Monday"))
    }

    @Test
    fun `3 days ago returns null`() {
        assertNull(regexParseDate("3 days ago"))
    }

    @Test
    fun `blank string returns null`() {
        assertNull(regexParseDate(""))
    }

    @Test
    fun `unrelated input returns null`() {
        assertNull(regexParseDate("wear count"))
    }
}
