package com.closet.features.chat.ai

import co.touchlab.kermit.Logger
import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.tools.ToolCallingChatProvider
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.di.AiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 *
 * @param aiHttpClient the AI inference client (120 s timeouts); see [AiHttpClient].
 */
class GeminiChatProvider(
    aiHttpClient: AiHttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : ChatAiProvider, ToolCallingChatProvider {

    private val client: HttpClient = aiHttpClient.client
    private val logger = Logger.withTag(TAG)

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
            logger.w("GeminiChatProvider inference failed (model=$model)", e)
            Result.failure(e)
        }
    }

    override suspend fun supportsTools(): Boolean = true

    override suspend fun step(
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): Result<ToolStep> {
        val apiKey = aiPreferencesRepository.getGeminiApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured"))
        }
        val model = aiPreferencesRepository.getGeminiModel().first()
            .takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        return try {
            val body = GeminiToolFormat.buildRequest(system, turns, tools, allowTools)
            val response = client.post("$BASE_URL/$model:generateContent") {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", apiKey)
                setBody(body.toString())
            }
            handleResponse(response.status.value, response.bodyAsText(), model, GeminiToolFormat.roundOf(turns))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val redacted = IllegalStateException("GeminiChatProvider tool step failed (model=$model)", e)
            logger.w("tool step failed", redacted)
            Result.failure(redacted)
        }
    }

    internal fun handleResponse(status: Int, body: String, model: String, round: Int): Result<ToolStep> {
        if (status !in 200..299) {
            logger.w("Gemini tool step failed: http=$status model=$model")
            return Result.failure(IllegalStateException("GeminiChatProvider tool step failed (model=$model, http=$status)"))
        }
        return try {
            Result.success(GeminiToolFormat.parseResponse(body, json, round))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(IllegalStateException("GeminiChatProvider tool response unreadable (model=$model)", e))
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
        append("\"generationConfig\":{\"responseMimeType\":\"application/json\"},")
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
                else     -> if (c.code < 0x20) append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }
}
