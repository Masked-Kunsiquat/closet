package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatAiProvider] backed by the MLKit GenAI Prompt API (Gemini Nano on-device).
 *
 * No API key required. Runs entirely on-device via Android AICore. Available on
 * supported devices (Pixel 6+, Galaxy S24+, etc.) once the Nano model is downloaded.
 *
 * Returns [Result.failure] immediately if [Generation.getClient] reports a status
 * other than [FeatureStatus.AVAILABLE] — callers should check
 * [com.closet.core.data.repository.AiPreferencesRepository.getAiReady] before invoking.
 *
 * The prompt combines [ChatPromptPrefix.SYSTEM_PROMPT], the wardrobe context block,
 * and the user message in a single string (Nano's Prompt API does not support
 * separate system/user turns). Response is parsed by [ChatResponseParser].
 */
@Singleton
class NanoChatProvider @Inject constructor(
    private val json: Json,
) : ChatAiProvider {

    private val model by lazy { Generation.getClient() }

    companion object {
        private const val TAG = "NanoChatProvider"
    }

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
        val status = try {
            model.checkStatus()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider: checkStatus threw — AICore unavailable")
            return Result.failure(e)
        }

        if (status != FeatureStatus.AVAILABLE) {
            return Result.failure(
                UnsupportedOperationException("Gemini Nano is not available on this device (status=$status)")
            )
        }

        return try {
            // Nano's Prompt API is single-string only — prepend the last 1 exchange (2 turns)
            // as a formatted text block rather than a message array.
            val historyPrefix = history.takeLast(2)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n", postfix = "\n\n") { turn ->
                    val label = if (turn.role == ConversationTurn.Role.User) "User" else "Assistant"
                    "$label: ${turn.text}"
                }
                .orEmpty()
            val prompt = "${ChatPromptPrefix.SYSTEM_PROMPT}\n\nContext:\n$context\n\n${historyPrefix}User: $userMessage"
            val response = model.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.2f
                    topK = 40
                    maxOutputTokens = 1024
                }
            )
            val responseText = response.candidates.firstOrNull()?.text
                ?: error("Empty response from Gemini Nano")
            ChatResponseParser.parse(responseText)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoChatProvider inference failed")
            Result.failure(e)
        }
    }
}
