package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.di.AiHttpClient
import com.closet.core.data.repository.AiPreferencesRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
 * [ChatAiProvider] backed by any OpenAI Chat Completions-compatible HTTP endpoint.
 *
 * Compatible with: OpenAI, Google Gemini cloud (via OpenAI-compatible endpoint), Ollama, Groq,
 * and any other service that exposes `POST /v1/chat/completions` with OpenAI request/response
 * shapes.
 *
 * The system message contains both [ChatPromptPrefix.SYSTEM_PROMPT] and the wardrobe
 * [context] block; the user turn contains only the raw [userMessage].
 *
 * Configuration (all stored in [AiPreferencesRepository]):
 * - **API key**  — `Authorization: Bearer <key>`
 * - **Base URL** — defaults to `https://api.openai.com`. Override for Ollama / Groq / etc.
 * - **Model**    — defaults to [DEFAULT_MODEL]
 */
@Singleton
class OpenAiChatProvider @Inject constructor(
    @AiHttpClient private val client: HttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : ChatAiProvider {

    companion object {
        private const val TAG = "OpenAiChatProvider"
        private const val DEFAULT_BASE_URL = "https://api.openai.com"
        internal const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    override suspend fun chat(userMessage: String, context: String): Result<ChatResponse> {
        val apiKey = aiPreferencesRepository.getOpenAiApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("OpenAI-compatible API key is not configured"))
        }

        val model = aiPreferencesRepository.getOpenAiModel().first().takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL
        val systemContent = "${ChatPromptPrefix.SYSTEM_PROMPT}\n\n$context"

        return try {
            val rawBaseUrl = aiPreferencesRepository.getOpenAiBaseUrl().first()
            val completionsUrl = buildCompletionsUrl(rawBaseUrl)
            val requestBody = buildRequestBody(model, systemContent, userMessage)

            val responseText: String = client.post(completionsUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(requestBody)
            }.body()

            val content = extractContent(responseText)
            ChatResponseParser.parse(content)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "OpenAiChatProvider inference failed (model=%s)", model)
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the full chat-completions endpoint URL from the user-supplied base.
     *
     * Rules:
     * - Blank → use [DEFAULT_BASE_URL] + `/v1/chat/completions`.
     * - Base has no path (or only `/`) → append `/v1/chat/completions`.
     * - Base already has a non-trivial path → append only `/chat/completions`.
     */
    private fun buildCompletionsUrl(raw: String): String {
        val base = (if (raw.isBlank()) DEFAULT_BASE_URL else raw).trimEnd('/')
        val path = try { java.net.URI(base).path ?: "" } catch (e: Exception) {
            throw IllegalArgumentException("Invalid OpenAI base URL — ${e.message}", e)
        }
        return if (path.isBlank() || path == "/") "$base/v1/chat/completions" else "$base/chat/completions"
    }

    private fun buildRequestBody(model: String, systemContent: String, userMessage: String): String =
        buildString {
            append("{")
            append("\"model\":${model.asJsonString()},")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":${systemContent.asJsonString()}},")
            append("{\"role\":\"user\",\"content\":${userMessage.asJsonString()}}")
            append("]")
            append("}")
        }

    private fun extractContent(responseText: String): String {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: error("choices missing from OpenAI response")
        check(choices.isNotEmpty()) { "choices array is empty in OpenAI response" }
        return choices[0].jsonObject["message"]
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: error("choices[0].message.content missing from OpenAI response")
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
