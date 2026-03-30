package com.closet.features.recommendations.ai

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OutfitSelection
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.di.AiHttpClient
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
import kotlinx.serialization.json.int
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
 * failure, fewer than 3 valid selections in the response. Callers treat every failure as a
 * silent fallback to the programmatic top-3 result — this class never throws.
 *
 * Not bound in [com.closet.features.recommendations.di.RecommendationModule] — the Settings
 * UI provider selector switches between [NanoProvider], [OpenAiProvider], and future providers
 * at runtime.
 */
@Singleton
class OpenAiProvider @Inject constructor(
    @AiHttpClient private val client: HttpClient,
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

    override suspend fun selectOutfits(
        combos: List<OutfitComboPayload>,
        styleVibe: String,
    ): Result<List<OutfitSelection>> {
        if (combos.isEmpty()) {
            return Result.failure(IllegalArgumentException("Combo list is empty"))
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

        return try {
            val comboJson = buildComboJson(combos)
            val requestBody = buildRequestBody(model, styleVibe, comboJson)

            val responseText: String = client.post("$baseUrl$CHAT_COMPLETIONS_PATH") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(requestBody)
            }.body<String>()

            Result.success(parseResponse(responseText, combos))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "OpenAiProvider inference failed (base=%s, model=%s)", baseUrl, model)
            Result.failure(e)
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
     *     { "role": "user",   "content": "Style vibe: <styleVibe>\n\nCombos:\n<comboJson>" }
     *   ]
     * }
     * ```
     */
    private fun buildRequestBody(model: String, styleVibe: String, comboJson: String): String =
        buildString {
            append("{")
            append("\"model\":${model.asJsonString()},")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":${SYSTEM_PROMPT.asJsonString()}},")
            append("{\"role\":\"user\",\"content\":${("Style vibe: $styleVibe\n\nCombos:\n$comboJson").asJsonString()}}")
            append("]")
            append("}")
        }

    /**
     * Serializes the combo list to compact JSON for the user message payload.
     *
     * Shape:
     * ```json
     * [{"combo_id":0,"items":[{"id":1,"name":"...","clothing_type":"...","material":"...","outfit_role":"...","layer":"...","color_families":["Neutral"],"is_pattern_solid":true,"suitability_score":0.9},...]}]
     * ```
     */
    private fun buildComboJson(combos: List<OutfitComboPayload>): String =
        buildString {
            append("[")
            combos.forEachIndexed { i, combo ->
                if (i > 0) append(",")
                append("{")
                append("\"combo_id\":${combo.comboId},")
                append("\"items\":[")
                combo.items.forEachIndexed { j, item ->
                    if (j > 0) append(",")
                    append(item.toJsonObject())
                }
                append("]}")
            }
            append("]")
        }

    private fun ClothingItemDto.toJsonObject(): String = buildString {
        append("{")
        append("\"id\":$id,")
        append("\"name\":${name.asJsonString()},")
        append("\"clothing_type\":${clothingType?.asJsonString() ?: "null"},")
        append("\"material\":${material?.asJsonString() ?: "null"},")
        append("\"outfit_role\":${outfitRole?.asJsonString() ?: "null"},")
        append("\"layer\":${layer?.asJsonString() ?: "null"},")
        append("\"color_families\":[${colorFamilies.joinToString(",") { it.asJsonString() }}],")
        append("\"is_pattern_solid\":$isPatternSolid,")
        append("\"suitability_score\":$suitabilityScore")
        append("}")
    }

    /** Wraps a string in JSON double-quotes, escaping backslashes, quotes, and control characters. */
    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")}\""

    /**
     * Parses the OpenAI Chat Completions response.
     *
     * The model content is expected to be a top-level JSON array of objects, each with
     * `combo_id` (Int) and `reason` (String):
     * ```json
     * [{"combo_id": 0, "reason": "..."}, {"combo_id": 3, "reason": "..."}, {"combo_id": 7, "reason": "..."}]
     * ```
     *
     * Outer OpenAI envelope shape:
     * ```json
     * { "choices": [ { "message": { "content": "[...]" } } ] }
     * ```
     *
     * Throws [IllegalStateException] on any structural mismatch or fewer than 3 valid
     * selections — callers catch via [runCatching].
     */
    private fun parseResponse(responseText: String, combos: List<OutfitComboPayload>): List<OutfitSelection> {
        val validComboIds = combos.map { it.comboId }.toSet()

        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: error("choices missing from OpenAI response")
        check(choices.isNotEmpty()) { "choices array is empty in OpenAI response" }

        val content = choices[0].jsonObject["message"]
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: error("choices[0].message.content missing from OpenAI response")

        val array = json.parseToJsonElement(content.trim()).jsonArray
        val selections = array.mapNotNull { element ->
            val obj = element.jsonObject
            val comboId = obj["combo_id"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val reason = obj["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (comboId !in validComboIds) {
                Timber.tag(TAG).w("OpenAI returned combo_id=%d not in input — discarding", comboId)
                return@mapNotNull null
            }
            OutfitSelection(comboId = comboId, reason = reason)
        }
        val distinct = selections.distinctBy { it.comboId }
        check(distinct.size >= 3) {
            "OpenAI returned only ${distinct.size} distinct valid selections — need at least 3"
        }
        return distinct.take(3)
    }
}
