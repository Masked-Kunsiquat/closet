package com.closet.features.chat

/** Parses a natural-language date out of a chat message. */
interface ChatDateParser {
    /** Returns an ISO date string (`YYYY-MM-DD`) or `null`. */
    suspend fun parseDate(text: String): String?
}
