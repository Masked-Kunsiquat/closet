package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor stub for [NanoChatProvider].
 *
 * Google Play Services and the MLKit GenAI Prompt API are not included in the
 * FOSS build. This stub satisfies the [ChatAiProvider] binding so the rest of
 * the codebase compiles unchanged.
 *
 * [chat] immediately returns [Result.failure] with [UnsupportedOperationException],
 * which [ChatAiProviderSelector] surfaces as a provider-not-ready error — the same
 * path a real unsupported device takes in the full flavor.
 *
 * Mirrors the pattern in `features/recommendations/src/foss/` for [NanoProvider].
 */
@Singleton
class NanoChatProvider @Inject constructor(
    private val json: Json,
) : ChatAiProvider {

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> = Result.failure(
        UnsupportedOperationException("Gemini Nano is not available in the FOSS build")
    )
}
