package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn

/**
 * No-op [OnDeviceChatProvider] for builds without Google Play Services / MLKit GenAI.
 *
 * [chat] immediately returns [Result.failure] with [UnsupportedOperationException],
 * which [ChatAiProviderSelector] surfaces as a provider-not-ready error — the same
 * path a real unsupported device takes in the full flavor.
 */
class UnavailableOnDeviceChatProvider : OnDeviceChatProvider {

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> = Result.failure(
        UnsupportedOperationException("Gemini Nano is not available in the FOSS build")
    )
}
