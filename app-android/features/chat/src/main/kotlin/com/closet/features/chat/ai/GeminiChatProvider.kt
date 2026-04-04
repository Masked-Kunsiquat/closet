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
 * [ChatAiProvider] backed by the Google Gemini native generateContent API.
 *
 * Calls `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
 * with the API key as a query parameter. Uses the Gemini-native request shape
 * (`system_instruction` + `contents`) rather than the OpenAI-compatible format.
 *
 * Configuration (all stored in [AiPreferencesRepository]):
 * - **API key** — [AiPreferencesRepository.getGeminiApiKey]; sent as `x-goog-api-key` header
 * - **Model**   — [AiPreferencesRepository.getGeminiModel]; defaults to [DEFAULT_MODEL]
 *
 * Response: `candidates[0].content.parts[0].text` parsed by [ChatResponseParser].
 */
@Singleton
class GeminiChatProvider @Inject constructor(
    @AiHttpClient private val client: HttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : ChatAiProvider {

    companion object {
        private const val TAG = "GeminiChatProvider"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        internal const val DEFAULT_MODEL = "gemini-2.0-flash-lite"
    }

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
        val apiKey = aiPreferencesRepository.getGeminiApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured"))
        }

        val model = aiPreferencesRepository.getGeminiModel().first()
            .takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        val endpoint = "$BASE_URL/$model:generateContent"
        val systemInstruction = "${ChatPromptPrefix.SYSTEM_PROMPT}\n\n$context"

        return try {
            val requestBody = buildRequestBody(systemInstruction, userMessage, history)

            val responseText: String = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", apiKey)
                setBody(requestBody)
            }.body()

            val text = extractText(responseText)
            ChatResponseParser.parse(text)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "GeminiChatProvider inference failed (model=%s)", model)
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildRequestBody(
        systemInstruction: String,
        userMessage: String,
        history: List<ConversationTurn>,
    ): String = buildString {
        append("{")
        append("\"system_instruction\":{\"parts\":[{\"text\":${systemInstruction.asJsonString()}}]},")
        append("\"contents\":[")
        history.forEachIndexed { i, turn ->
            // Gemini uses "model" for assistant turns, not "assistant"
            val role = if (turn.role == ConversationTurn.Role.User) "user" else "model"
            if (i > 0) append(",")
            append("{\"role\":\"$role\",\"parts\":[{\"text\":${turn.text.asJsonString()}}]}")
        }
        if (history.isNotEmpty()) append(",")
        append("{\"role\":\"user\",\"parts\":[{\"text\":${userMessage.asJsonString()}}]}")
        append("]")
        append("}")
    }

    private fun extractText(responseText: String): String {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: error("candidates missing from Gemini response")
        check(candidates.isNotEmpty()) { "candidates array is empty in Gemini response" }
        val parts = candidates[0].jsonObject["content"]
            ?.jsonObject?.get("parts")
            ?.jsonArray
            ?: error("candidates[0].content.parts missing from Gemini response")
        check(parts.isNotEmpty()) { "parts array is empty in Gemini response" }
        return parts[0].jsonObject["text"]?.jsonPrimitive?.content
            ?: error("candidates[0].content.parts[0].text missing from Gemini response")
    }

    /** Wraps a string in JSON double-quotes, escaping backslashes, quotes, and all U+0000..U+001F control chars. */
    private fun String.asJsonString(): String = buildString {
        append('"')
        for (c in this@asJsonString) {
            when (c) {
                '"'      -> append("\\\"")
                '\\'     -> append("\\\\")
                '\n'     -> append("\\n")
                '\r'     -> append("\\r")
                '\t'     -> append("\\t")
                '\b'     -> append("\\b")
                '\u000C' -> append("\\f")
                else     -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
