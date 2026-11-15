package com.closet.features.chat

/**
 * FOSS-flavor [ChatDateParser]: regex-only date parsing via [regexParseDate].
 * ML Kit Entity Extraction is not available in the FOSS build (requires Play Services).
 */
class ChatDateParser {
    suspend fun parseDate(text: String): String? = regexParseDate(text)
}
