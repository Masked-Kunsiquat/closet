package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn

/**
 * No-op [OnDeviceChatProvider] used where on-device chat is unavailable (foss today; iOS/desktop later).
 *
 * [chat] immediately returns [Result.failure] with [UnsupportedOperationException],
 * which [ChatAiProviderSelector] surfaces as a provider-not-ready error — the same
 * path a real unsupported device takes with the GMS implementation.
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
