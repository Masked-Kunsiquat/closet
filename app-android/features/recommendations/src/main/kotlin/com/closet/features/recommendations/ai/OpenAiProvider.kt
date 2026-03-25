package com.closet.features.recommendations.ai

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OutfitSuggestion
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
import kotlinx.serialization.json.long
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// OpenAiProvider
// ---------------------------------------------------------------------------

/**
 * [OutfitAiProvider] backed by any OpenAI Chat Completions-compatible HTTP endpoint.
 *
 * Compatible with: OpenAI, Google Gemini cloud (v1beta), Ollama, Groq, Mistral, and any
 * other service that exposes `POST /v1/chat/completions` with OpenAI request/response shapes.
 *
 * Configuration (all stored in [AiPreferencesRepository]):
 * - **API key** — `Authorization: Bearer <key>`. Required for cloud providers; Ollama accepts
 *   any non-empty string.
 * - **Base URL** — defaults to `https://api.openai.com`. Override for Ollama / Groq / etc.
 *   Trailing slashes are normalized before use.
 * - **Model** — defaults to `gpt-4o-mini`. Override per provider (e.g. `gemini-2.0-flash`,
 *   `llama3`, `mixtral-8x7b-32768`).
 *
 * Returns [Result.failure] on any of: missing API key, HTTP error, network error, JSON parse
 * failure, empty `choices` array. Callers treat every failure as a silent fallback to the
 * programmatic top-3 result — this class never throws.
 *
 * Not bound in [com.closet.features.recommendations.di.RecommendationModule] — Phase 2
 * Settings UI will introduce a runtime provider selector that switches between
 * [NanoProvider], [OpenAiProvider], and future providers.
 */
@Singleton
class OpenAiProvider @Inject constructor(
    private val client: HttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : OutfitAiProvider {

    companion object {
        private const val TAG = "OpenAiProvider"
        private const val DEFAULT_BASE_URL = "https://api.openai.com"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"

        /** Shared system prompt from [OutfitPromptPrefix] — identical contract for all providers. */
        private val SYSTEM_PROMPT get() = OutfitPromptPrefix.SYSTEM_PROMPT
    }

    override suspend fun suggestOutfit(candidates: List<ClothingItemDto>): Result<OutfitSuggestion> {
        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("Candidate list is empty"))
        }

        val apiKey = aiPreferencesRepository.getOpenAiApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("OpenAI-compatible API key is not configured")
            )
        }

        val rawBaseUrl = aiPreferencesRepository.getOpenAiBaseUrl().first()
        val baseUrl = resolveBaseUrl(rawBaseUrl)
        val model = aiPreferencesRepository.getOpenAiModel().first().takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL

        return runCatching {
            val candidateJson = buildCandidateJson(candidates)
            val requestBody = buildRequestBody(model, candidateJson)

            val responseText: String = client.post("$baseUrl$CHAT_COMPLETIONS_PATH") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(requestBody)
            }.body<String>()

            parseResponse(responseText)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "OpenAiProvider inference failed (base=%s, model=%s)", baseUrl, model)
        }
    }

    /**
     * Normalizes the user-supplied base URL:
     * - Falls back to [DEFAULT_BASE_URL] when blank.
     * - Strips a trailing slash so path concatenation is always `baseUrl + "/v1/chat/completions"`.
     */
    private fun resolveBaseUrl(raw: String): String =
        (if (raw.isBlank()) DEFAULT_BASE_URL else raw).trimEnd('/')

    /**
     * Builds the OpenAI Chat Completions request body as a raw JSON string.
     *
     * Manual serialization keeps the request shape self-contained and avoids
     * declaring internal DTOs annotated with @Serializable in this module.
     *
     * Shape:
     * ```json
     * {
     *   "model": "gpt-4o-mini",
     *   "messages": [
     *     { "role": "system", "content": "<SYSTEM_PROMPT>" },
     *     { "role": "user",   "content": "Candidates:\n<candidateJson>" }
     *   ]
     * }
     * ```
     */
    private fun buildRequestBody(model: String, candidateJson: String): String =
        buildString {
            append("{")
            append("\"model\":${model.asJsonString()},")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":${SYSTEM_PROMPT.asJsonString()}},")
            append("{\"role\":\"user\",\"content\":${("Candidates:\n$candidateJson").asJsonString()}}")
            append("]")
            append("}")
        }

    /**
     * Serializes the candidate list to compact JSON for the user message payload.
     * Manual serialization mirrors [NanoProvider.buildCandidateJson] exactly — avoids
     * requiring [ClothingItemDto] to be annotated with @Serializable in core/data.
     */
    private fun buildCandidateJson(candidates: List<ClothingItemDto>): String =
        buildString {
            append("[")
            candidates.forEachIndexed { i, item ->
                if (i > 0) append(",")
                append("{")
                append("\"id\":${item.id},")
                append("\"name\":${item.name.asJsonString()},")
                append("\"outfit_role\":${item.outfitRole?.asJsonString() ?: "null"},")
                append("\"color_families\":[${item.colorFamilies.joinToString(",") { it.asJsonString() }}],")
                append("\"is_pattern_solid\":${item.isPatternSolid},")
                append("\"suitability_score\":${item.suitabilityScore}")
                append("}")
            }
            append("]")
        }

    /** Wraps a string in JSON double-quotes, escaping internal quotes and backslashes. */
    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    /**
     * Parses the OpenAI Chat Completions response.
     *
     * Expected shape:
     * ```json
     * { "choices": [ { "message": { "content": "{\"selected_ids\":[...],\"reason\":\"...\"}" } } ] }
     * ```
     *
     * Throws [IllegalStateException] on any structural mismatch or empty choices — callers
     * catch via [runCatching].
     */
    private fun parseResponse(responseText: String): OutfitSuggestion {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: error("choices missing from OpenAI response")
        check(choices.isNotEmpty()) { "choices array is empty in OpenAI response" }

        val content = choices[0].jsonObject["message"]
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: error("choices[0].message.content missing from OpenAI response")

        val inner = json.parseToJsonElement(content.trim()).jsonObject
        val ids = inner["selected_ids"]?.jsonArray?.map { it.jsonPrimitive.long }
            ?: error("selected_ids missing from model response content")
        check(ids.isNotEmpty()) { "selected_ids array is empty in model response content" }
        val reason = inner["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        return OutfitSuggestion(selectedIds = ids, reason = reason)
    }
}
