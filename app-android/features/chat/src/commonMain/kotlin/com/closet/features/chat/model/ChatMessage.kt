package com.closet.features.chat.model

import com.closet.core.data.ai.ChatAction

sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    sealed interface Assistant : ChatMessage {
        data class Text(val text: String) : Assistant
        data class WithItems(
            val text: String,
            val items: List<ChatItemSummary>,
            val action: ChatAction? = null,
        ) : Assistant
        data class WithOutfit(
            val text: String,
            val items: List<ChatItemSummary>,
            val reason: String,
            val action: ChatAction? = null,
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
 * [imagePath] is the resolved absolute path of the item's image, or null if the item has no image.
 * Existence is not checked; the image loader's error slot handles a missing file.
 */
data class ChatItemSummary(
    val id: Long,
    val name: String,
    val imagePath: String?,
)
