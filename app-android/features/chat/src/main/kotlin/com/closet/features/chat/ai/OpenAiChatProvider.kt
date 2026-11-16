package com.closet.features.chat.ai

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.tools.ToolCallingChatProvider
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.closet.core.data.ai.tools.ToolsUnsupportedException
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.di.AiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

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
 *
 * @param aiHttpClient the AI inference client (120 s timeouts); see [AiHttpClient].
 */
class OpenAiChatProvider(
    aiHttpClient: AiHttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : ChatAiProvider, ToolCallingChatProvider {

    private val client: HttpClient = aiHttpClient.client

    companion object {
        private const val TAG = "OpenAiChatProvider"
        private const val DEFAULT_BASE_URL = "https://api.openai.com"
        internal const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    override suspend fun chat(
        userMessage: String,
        context: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
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
            val requestBody = buildRequestBody(model, systemContent, userMessage, history)

            val responseText: String = client.post(completionsUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(requestBody)
            }.body()

            val content = extractContent(responseText)
            ChatResponseParser.parse(content)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val redacted = IllegalStateException("OpenAiChatProvider inference failed (model=$model)", e)
            Timber.tag(TAG).w(redacted, "inference failed")
            Result.failure(redacted)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Set once the server rejects `tools`/`tool_choice`; stays set for this instance. */
    @Volatile
    private var toolsRejected = false

    override suspend fun supportsTools(): Boolean = !toolsRejected

    override suspend fun step(
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): Result<ToolStep> {
        if (toolsRejected) return Result.failure(ToolsUnsupportedException("server rejected tools"))
        val apiKey = aiPreferencesRepository.getOpenAiApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("OpenAI-compatible API key is not configured"))
        }
        val model = aiPreferencesRepository.getOpenAiModel().first().takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL
        return try {
            val url = buildCompletionsUrl(aiPreferencesRepository.getOpenAiBaseUrl().first())
            val body = OpenAiToolFormat.buildRequest(model, system, turns, tools, allowTools)
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(body.toString())
            }
            handleResponse(response.status.value, response.bodyAsText(), model)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val redacted = IllegalStateException("OpenAiChatProvider tool step failed (model=$model)", e)
            Timber.tag(TAG).w(redacted, "tool step failed")
            Result.failure(redacted)
        }
    }

    internal fun handleResponse(status: Int, body: String, model: String): Result<ToolStep> {
        if (status !in 200..299) {
            if (OpenAiToolFormat.isToolsUnsupported(status, body)) {
                toolsRejected = true
                return Result.failure(ToolsUnsupportedException("HTTP $status rejected tools"))
            }
            return Result.failure(IllegalStateException("OpenAiChatProvider tool step failed (model=$model, http=$status)"))
        }
        return try {
            Result.success(OpenAiToolFormat.parseResponse(body, json))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(IllegalStateException("OpenAiChatProvider tool response unreadable (model=$model)", e))
        }
    }

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

    private fun buildRequestBody(
        model: String,
        systemContent: String,
        userMessage: String,
        history: List<ConversationTurn>,
    ): String = buildString {
        append("{")
        append("\"model\":${model.asJsonString()},")
        append("\"messages\":[")
        append("{\"role\":\"system\",\"content\":${systemContent.asJsonString()}}")
        history.forEach { turn ->
            val role = if (turn.role == ConversationTurn.Role.User) "user" else "assistant"
            append(",{\"role\":\"$role\",\"content\":${turn.text.asJsonString()}}")
        }
        append(",{\"role\":\"user\",\"content\":${userMessage.asJsonString()}}")
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
