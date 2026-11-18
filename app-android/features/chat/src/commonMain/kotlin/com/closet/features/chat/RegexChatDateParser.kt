package com.closet.features.chat

import com.closet.core.data.time.AppClock

/** Regex-only [ChatDateParser] via [regexParseDate]; no ML Kit / Play Services required. */
class RegexChatDateParser(private val clock: AppClock) : ChatDateParser {
    override suspend fun parseDate(text: String): String? = regexParseDate(text, clock.today())
}
