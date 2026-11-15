package com.closet.features.chat

/** Decides whether a chat message is confidently English. */
interface LanguageDetector {
    suspend fun isEnglish(text: String): Boolean
}
