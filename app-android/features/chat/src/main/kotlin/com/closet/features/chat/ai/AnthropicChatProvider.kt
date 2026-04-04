package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.di.AiHttpClient
import com.closet.core.data.repository.AiPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatAiProvider] backed by the Anthropic Messages API (Claude).
 *
 * Calls `POST https://api.anthropic.com/v1/messages` with the `x-api-key` /
 * `anthropic-version` header pair. The system prompt (including the wardrobe
 * context block) is passed as the top-level `"system"` field; the user question
 * is the single `"user"` message. The response is extracted from `content[0].text`
 * and parsed into a [ChatResponse] via [ChatResponseParser].
 *
 * Configuration:
 * - **API key** — [AiPreferencesRepository.getAnthropicApiKey]
 * - **Model**   — [AiPreferencesRepository.getAnthropicModel]; defaults to [DEFAULT_MODEL]
 */
@Singleton
class AnthropicChatProvider @Inject constructor(
    @AiHttpClient private val client: HttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : ChatAiProvider {

    companion object {
        private const val TAG = "AnthropicChatProvider"
        private const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION_HEADER = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val API_KEY_HEADER = "x-api-key"
        internal const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
        private const val MAX_OUTPUT_TOKENS = 1024
    }

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
        val apiKey = aiPreferencesRepository.getAnthropicApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Anthropic API key is not configured"))
        }

        val model = aiPreferencesRepository.getAnthropicModel().first()
            .takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        val systemPrompt = "${ChatPromptPrefix.SYSTEM_PROMPT}\n\n$context"

        return try {
            val requestBody = buildRequestBody(model, systemPrompt, userMessage, history)

            val responseText: String = client.post(MESSAGES_ENDPOINT) {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, apiKey)
                header(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION)
                setBody(requestBody)
            }.body()

            val text = extractText(responseText)
            ChatResponseParser.parse(text)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "AnthropicChatProvider inference failed")
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildRequestBody(
        model: String,
        systemPrompt: String,
        userMessage: String,
        history: List<ConversationTurn>,
    ): String = buildString {
        append("{")
        append("\"model\":${model.asJsonString()},")
        append("\"max_tokens\":$MAX_OUTPUT_TOKENS,")
        append("\"system\":${systemPrompt.asJsonString()},")
        append("\"messages\":[")
        history.forEach { turn ->
            val role = if (turn.role == ConversationTurn.Role.User) "user" else "assistant"
            append("{\"role\":\"$role\",\"content\":${turn.text.asJsonString()}},")
        }
        append("{\"role\":\"user\",\"content\":${userMessage.asJsonString()}}")
        append("]")
        append("}")
    }

    private fun extractText(responseText: String): String {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val contentArray = root["content"]?.jsonArray
            ?: error("content missing from Anthropic response")
        check(contentArray.isNotEmpty()) { "content array is empty in Anthropic response" }
        return contentArray[0].jsonObject["text"]?.jsonPrimitive?.content
            ?: error("content[0].text missing from Anthropic response")
    }

    /** Wraps a string in JSON double-quotes, escaping backslashes, control characters, and quotes. */
    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\"", "\\\"")}\""
}
