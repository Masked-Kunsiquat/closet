package com.closet.features.chat

/** Regex-only [ChatDateParser] via [regexParseDate]; no ML Kit / Play Services required. */
class RegexChatDateParser : ChatDateParser {
    override suspend fun parseDate(text: String): String? = regexParseDate(text)
}
