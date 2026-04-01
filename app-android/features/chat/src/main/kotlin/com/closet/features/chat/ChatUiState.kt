package com.closet.features.chat

import com.closet.features.chat.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val providerLabel: String = "AI",
)
