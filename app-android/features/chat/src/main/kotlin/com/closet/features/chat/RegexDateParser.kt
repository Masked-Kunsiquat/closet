package com.closet.features.chat

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Regex-based date extraction shared between the full and FOSS [ChatDateParser] implementations.
 *
 * Only handles unambiguous, explicit date formats:
 * - ISO:          `2026-04-04`
 * - Month Day:    `April 4`, `April 4th`, `Apr 4`
 * - Month + Year: `April 4, 2026`
 *
 * Relative expressions ("yesterday", "last Monday") are intentionally not handled here —
 * those are the ML Kit Entity Extraction upgrade path in the full flavor.
 *
 * @return An ISO date string (`YYYY-MM-DD`) or `null` if no date can be parsed.
 */
internal fun regexParseDate(text: String): String? {
    val lower = text.lowercase(Locale.ENGLISH)

    // ISO date — fast path.
    ISO_DATE_PATTERN.find(lower)?.value?.let { return it }

    // "Month Day" or "Month Day, Year"
    val match = MONTH_DAY_PATTERN.find(lower) ?: return null
    val monthStr  = match.groupValues[1]
    val dayStr    = match.groupValues[2].replace(ORDINAL_SUFFIX, "")
    val yearStr   = match.groupValues[3].trim().trimStart(',').trim()

    val year     = yearStr.toIntOrNull() ?: LocalDate.now().year
    val monthNum = MONTH_NAMES[monthStr.lowercase(Locale.ENGLISH)] ?: return null

    return try {
        LocalDate.of(year, monthNum, dayStr.toInt())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (e: Exception) {
        null
    }
}

private val ISO_DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

private val MONTH_DAY_PATTERN = Regex(
    """(january|february|march|april|may|june|july|august|september|october|november|december|""" +
    """jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\.?\s+(\d{1,2}(?:st|nd|rd|th)?),?\s*(\d{4})?""",
    RegexOption.IGNORE_CASE
)

private val ORDINAL_SUFFIX = Regex("""st|nd|rd|th""")

private val MONTH_NAMES = mapOf(
    "january" to 1, "jan" to 1,
    "february" to 2, "feb" to 2,
    "march" to 3, "mar" to 3,
    "april" to 4, "apr" to 4,
    "may" to 5,
    "june" to 6, "jun" to 6,
    "july" to 7, "jul" to 7,
    "august" to 8, "aug" to 8,
    "september" to 9, "sep" to 9,
    "october" to 10, "oct" to 10,
    "november" to 11, "nov" to 11,
    "december" to 12, "dec" to 12,
)
