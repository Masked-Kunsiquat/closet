package com.closet.features.chat

import com.closet.features.chat.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val providerLabel: String = "AI",
    /**
     * `false` when the embedding index has no vectors yet — either because
     * [EmbeddingWorker] hasn't run yet or because the wardrobe has no items.
     * The UI surfaces a notice so the user understands why results may be thin.
     * Refreshed each time [ChatViewModel.sendMessage] is called.
     */
    val isIndexReady: Boolean = true,
)
