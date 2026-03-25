package com.closet.features.recommendations.ai

import android.content.Context
import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Internal inference engine abstraction — isolates the MLKit API surface
// ---------------------------------------------------------------------------

/**
 * Internal abstraction over the MLKit GenAI Prompt API.
 *
 * Introduced so that [NanoProvider] can be compiled and injected independently
 * of whether the MLKit `genai-inference` artifact is available on the current
 * Android Maven channel. When the artifact is published, replace [StubNanoInferenceEngine]
 * with a real implementation in `features/recommendations/ai/`.
 *
 * See: https://developer.android.com/ai/aicore (API reference TBD at GA)
 */
internal interface NanoInferenceEngine {
    /**
     * Checks whether Gemini Nano is supported and downloaded on this device.
     * Returns false if the device doesn't meet minimum requirements (Pixel 6+, Android 10+,
     * ~1.5 GB free storage).
     */
    fun isAvailable(): Boolean

    /**
     * Runs inference and returns the raw model output text.
     * Throws on model error or [com.google.mlkit.common.MlKitException].
     */
    suspend fun generate(prompt: String): String
}

/**
 * Placeholder implementation used until `com.google.mlkit:genai-inference` lands on
 * Google Maven. Always reports unavailable so callers fall back to programmatic results.
 *
 * TODO: replace with the real MLKit implementation when the artifact is published.
 *   Expected coordinates: com.google.mlkit:genai-common / com.google.mlkit:genai-inference
 *   Minimum versions: as declared in gradle/libs.versions.toml (mlkitGenaiCommon / mlkitGenaiInference)
 */
internal class StubNanoInferenceEngine : NanoInferenceEngine {
    override fun isAvailable(): Boolean = false
    override suspend fun generate(prompt: String): String =
        error("Gemini Nano not yet available — MLKit genai-inference artifact not published")
}

// ---------------------------------------------------------------------------
// NanoProvider
// ---------------------------------------------------------------------------

/**
 * [OutfitAiProvider] backed by the MLKit GenAI Prompt API (Gemini Nano on-device).
 *
 * No API key required. Available on supported devices (Pixel 6+, Android 10+) after
 * model download (~1.5 GB). Callers must check
 * [com.closet.core.data.repository.AiPreferencesRepository.getAiReady] before invoking —
 * this provider will return [Result.failure] immediately if Nano is not available.
 *
 * Token management (countTokens / trim payload) is performed by the coherence scorer
 * layer (Phase 2 wiring), not here.
 *
 * Prompt design — constrained selection only:
 * The model selects IDs from the provided candidate list; it cannot hallucinate clothing
 * data. Response format (JSON only, no preamble):
 * ```json
 * { "selected_ids": [1, 4, 7], "reason": "..." }
 * ```
 * Any response that fails JSON parsing or contains IDs not in the candidate list is
 * discarded silently by the caller, which falls back to the programmatic top-3.
 *
 * Bound to [OutfitAiProvider] via [com.closet.features.recommendations.di.RecommendationModule].
 */
@Singleton
class NanoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : OutfitAiProvider {

    // TODO: replace StubNanoInferenceEngine with the real MLKit-backed implementation when
    //   com.google.mlkit:genai-inference lands on Google Maven.
    //   The engine field is intentionally not injected so the internal type stays hidden.
    private val engine: NanoInferenceEngine = StubNanoInferenceEngine()

    companion object {
        private const val TAG = "NanoProvider"

        /**
         * System prompt prefix — reused across requests.
         * Instructs the model on output format and selection constraints.
         */
        private val SYSTEM_PROMPT = """
            You are an outfit coherence scorer. You will be given a JSON array of clothing items.
            Select the best combination for a complete, coherent outfit.

            Rules:
            - Only select IDs from the list provided. Never invent new IDs.
            - A valid outfit requires: (one Top + one Bottom) OR (one OnePiece),
              with optional Outerwear, Footwear, or Accessory.
            - Prefer color harmony and avoid pattern clashes.
            - Higher suitability_score items are statistically better matches for today's conditions.

            Respond with ONLY valid JSON in this exact format — no preamble, no text outside the JSON:
            {"selected_ids": [<id1>, <id2>, ...], "reason": "<one sentence>"}
        """.trimIndent()
    }

    override suspend fun suggestOutfit(candidates: List<ClothingItemDto>): Result<OutfitSuggestion> {
        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("Candidate list is empty"))
        }

        if (!engine.isAvailable()) {
            return Result.failure(
                UnsupportedOperationException("Gemini Nano is not available on this device")
            )
        }

        return runCatching {
            val candidateJson = buildCandidateJson(candidates)
            val prompt = "$SYSTEM_PROMPT\n\nCandidates:\n$candidateJson"

            val responseText = engine.generate(prompt)
            parseResponse(responseText)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "NanoProvider inference failed")
        }
    }

    /**
     * Serializes the candidate list to compact JSON for the dynamic prompt suffix.
     * Manual serialization avoids requiring [ClothingItemDto] to be annotated with
     * @Serializable in core/data.
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
     * Parses the model's JSON response into an [OutfitSuggestion].
     * Throws [IllegalStateException] on parse failure — callers catch via [runCatching].
     */
    private fun parseResponse(responseText: String): OutfitSuggestion {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val ids = root["selected_ids"]?.jsonArray?.map { it.jsonPrimitive.long }
            ?: error("selected_ids missing from model response")
        check(ids.isNotEmpty()) { "selected_ids array is empty in model response" }
        val reason = root["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        return OutfitSuggestion(selectedIds = ids, reason = reason)
    }
}
