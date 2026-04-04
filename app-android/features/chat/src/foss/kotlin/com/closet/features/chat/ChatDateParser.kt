package com.closet.features.chat

import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor [ChatDateParser]: regex-only date parsing via [regexParseDate].
 * ML Kit Entity Extraction is not available in the FOSS build (requires Play Services).
 */
@Singleton
class ChatDateParser @Inject constructor() {
    suspend fun parseDate(text: String): String? = regexParseDate(text)
}
