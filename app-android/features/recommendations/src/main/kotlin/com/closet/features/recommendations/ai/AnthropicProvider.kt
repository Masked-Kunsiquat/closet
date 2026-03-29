package com.closet.features.recommendations.ai

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OutfitSelection
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
import kotlinx.serialization.json.int
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// AnthropicProvider
// ---------------------------------------------------------------------------

/**
 * [OutfitAiProvider] backed by the Anthropic Messages API (Claude).
 *
 * Calls `POST https://api.anthropic.com/v1/messages` with the `x-api-key` / `anthropic-version`
 * header pair required by the Anthropic API. Unlike the OpenAI Chat Completions shape, the
 * system prompt is a top-level field on the request body (not a `messages` entry with
 * `role: "system"`), and the response text is extracted from `content[0].text` rather than
 * `choices[0].message.content`.
 *
 * Configuration:
 * - **API key** — stored in [AiPreferencesRepository] via `getAnthropicApiKey()`.
 * - **Model** — defaults to `claude-haiku-4-5-20251001`. Haiku is intentionally chosen for
 *   low latency and cost on a mobile-triggered request.
 *
 * Returns [Result.failure] on: missing API key, HTTP error, network error, JSON parse failure,
 * or fewer than 3 distinct valid selections in the response. Callers treat every failure as a
 * silent fallback to the programmatic top-3 result — this class never throws.
 *
 * Not bound in [com.closet.features.recommendations.di.RecommendationModule] — it is injected
 * directly into [OutfitCoherenceScorer] alongside [NanoProvider] and [OpenAiProvider], and the
 * scorer's provider-selection switch picks the active implementation at runtime.
 */
@Singleton
class AnthropicProvider @Inject constructor(
    private val client: HttpClient,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val json: Json,
) : OutfitAiProvider {

    companion object {
        private const val TAG = "AnthropicProvider"
        private const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION_HEADER = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val API_KEY_HEADER = "x-api-key"
        /** Fallback model used when no model is configured in prefs. */
        internal const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
        private const val MAX_OUTPUT_TOKENS = 512

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

        val apiKey = aiPreferencesRepository.getAnthropicApiKey().first()
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("Anthropic API key is not configured")
            )
        }

        val model = aiPreferencesRepository.getAnthropicModel().first()
            .takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        return runCatching {
            val comboJson = buildComboJson(combos)
            val requestBody = buildRequestBody(model, styleVibe, comboJson)

            val responseText: String = client.post(MESSAGES_ENDPOINT) {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, apiKey)
                header(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION)
                setBody(requestBody)
            }.body<String>()

            parseResponse(responseText, combos)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "AnthropicProvider inference failed")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the Anthropic Messages API request body as a raw JSON string.
     *
     * The Anthropic API takes the system prompt as a top-level `"system"` field rather than
     * as a `messages` entry, and requires a `"max_tokens"` cap. The user message carries the
     * style vibe + serialized combo list.
     *
     * Shape:
     * ```json
     * {
     *   "model": "claude-haiku-4-5-20251001",
     *   "max_tokens": 512,
     *   "system": "<SYSTEM_PROMPT>",
     *   "messages": [
     *     { "role": "user", "content": "Style vibe: <styleVibe>\n\nCombos:\n<comboJson>" }
     *   ]
     * }
     * ```
     */
    private fun buildRequestBody(model: String, styleVibe: String, comboJson: String): String =
        buildString {
            append("{")
            append("\"model\":${model.asJsonString()},")
            append("\"max_tokens\":$MAX_OUTPUT_TOKENS,")
            append("\"system\":${SYSTEM_PROMPT.asJsonString()},")
            append("\"messages\":[")
            append("{\"role\":\"user\",\"content\":${("Style vibe: $styleVibe\n\nCombos:\n$comboJson").asJsonString()}}")
            append("]")
            append("}")
        }

    /**
     * Serializes the combo list to compact JSON for the user message payload.
     * Identical shape to [OpenAiProvider.buildComboJson].
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

    /** Wraps a string in JSON double-quotes, escaping backslashes, control characters, and quotes. */
    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\"", "\\\"")}\""

    /**
     * Parses the Anthropic Messages API response.
     *
     * The model content is a top-level JSON array of objects, each with `combo_id` (Int)
     * and `reason` (String), embedded in `content[0].text`:
     * ```json
     * { "content": [ { "type": "text", "text": "[{\"combo_id\":0,\"reason\":\"...\"}]" } ], ... }
     * ```
     *
     * Throws [IllegalStateException] on any structural mismatch or fewer than 3 distinct
     * valid selections — callers catch via [runCatching].
     */
    private fun parseResponse(responseText: String, combos: List<OutfitComboPayload>): List<OutfitSelection> {
        val validComboIds = combos.map { it.comboId }.toSet()

        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val contentArray = root["content"]?.jsonArray
            ?: error("content missing from Anthropic response")
        check(contentArray.isNotEmpty()) { "content array is empty in Anthropic response" }

        val text = contentArray[0].jsonObject["text"]?.jsonPrimitive?.content
            ?: error("content[0].text missing from Anthropic response")

        val array = json.parseToJsonElement(text.trim()).jsonArray
        val selections = array.mapNotNull { element ->
            val obj = element.jsonObject
            val comboId = obj["combo_id"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val reason = obj["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (comboId !in validComboIds) {
                Timber.tag(TAG).w("Anthropic returned combo_id=%d not in input — discarding", comboId)
                return@mapNotNull null
            }
            OutfitSelection(comboId = comboId, reason = reason)
        }
        val distinct = selections.distinctBy { it.comboId }
        check(distinct.size >= 3) {
            "Anthropic returned only ${distinct.size} distinct valid selections — need at least 3"
        }
        return distinct.take(3)
    }
}
