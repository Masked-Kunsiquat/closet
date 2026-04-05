package com.closet.features.chat.model

import java.io.File

sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    sealed interface Assistant : ChatMessage {
        data class Text(val text: String) : Assistant
        data class WithItems(val text: String, val items: List<ChatItemSummary>) : Assistant
        data class WithOutfit(
            val text: String,
            val items: List<ChatItemSummary>,
            val reason: String,
        ) : Assistant
        data class WithStat(
            val text: String,
            val label: String,
            val value: String,
            val items: List<ChatItemSummary>,
        ) : Assistant
        data object Thinking : Assistant
        data class Error(val text: String) : Assistant
    }
}

/**
 * Lightweight item representation for display inside a chat message.
 * [imageFile] is the resolved on-device [File] (already existence-checked in the ViewModel),
 * or null if the item has no image or the file is missing.
 */
data class ChatItemSummary(
    val id: Long,
    val name: String,
    val imageFile: File?,
)
