package com.closet.features.recommendations.ai

import android.content.Context
import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OutfitSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
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
     * Counts the number of tokens in [prompt] as reported by the on-device model.
     *
     * Used by [OutfitCoherenceScorer] to gate candidate payload size before inference.
     * Returns [Int.MAX_VALUE] in the stub implementation so the gate never trims the
     * payload until the real MLKit engine lands.
     *
     * TODO: replace stub return with real MLKit countTokens() when genai-inference lands.
     */
    suspend fun countTokens(prompt: String): Int

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
    /** Returns [Int.MAX_VALUE] so the token-count gate in [OutfitCoherenceScorer] never trims. */
    override suspend fun countTokens(prompt: String): Int = Int.MAX_VALUE
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
 * layer, not here.
 *
 * Prompt design — constrained selection only:
 * The model selects combo_ids from the provided combo list; it cannot hallucinate clothing
 * data. Response format (JSON array, no preamble):
 * ```json
 * [{"combo_id": 0, "reason": "..."}, {"combo_id": 3, "reason": "..."}, {"combo_id": 7, "reason": "..."}]
 * ```
 * Any response that fails JSON parsing, returns fewer than 3 valid entries, or contains
 * combo_ids not in the input is discarded by the caller, which falls back to programmatic top-3.
 *
 * Bound to [OutfitAiProvider] via [com.closet.features.recommendations.di.RecommendationModule].
 */
@Singleton
class NanoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : OutfitAiProvider, NanoInitializer {

    // TODO: replace StubNanoInferenceEngine with the real MLKit-backed implementation when
    //   com.google.mlkit:genai-inference lands on Google Maven.
    //   The engine field is intentionally not injected so the internal type stays hidden.
    private val engine: NanoInferenceEngine = StubNanoInferenceEngine()

    companion object {
        private const val TAG = "NanoProvider"

        /** Shared system prompt from [OutfitPromptPrefix] — identical contract for all providers. */
        private val SYSTEM_PROMPT get() = OutfitPromptPrefix.SYSTEM_PROMPT
    }

    // ── NanoInitializer ───────────────────────────────────────────────────────

    /**
     * Runs the Nano init sequence and emits progress + terminal result.
     *
     * Sequence:
     *   1. [NanoInferenceEngine.isAvailable] — if false → emit [NanoInitResult.NotSupported]
     *   2. Simulated download progress (real impl replaces with MLKit download stream)
     *   3. getTokenLimit() — stubbed as 0 until the real engine lands
     *   4. Emits [NanoInitResult.Success] with the token limit
     *
     * Because [StubNanoInferenceEngine.isAvailable] always returns false, this flow will
     * always emit [NanoInitResult.NotSupported] until the real MLKit implementation is wired.
     */
    override fun initNanoFlow(): Flow<NanoInitResult> = flow {
        Timber.tag(TAG).d("initNanoFlow: checking Nano availability")
        if (!engine.isAvailable()) {
            Timber.tag(TAG).d("initNanoFlow: Nano not supported on this device")
            emit(NanoInitResult.NotSupported)
            return@flow
        }

        // TODO: replace with real MLKit model download + progress streaming when
        //   com.google.mlkit:genai-inference lands on Google Maven.
        //   The real implementation should emit NanoInitResult.Downloading(pct) events
        //   from the download progress callback before emitting Success.
        try {
            // Stub: engine is available, proceed directly to Success with a placeholder token limit.
            // Real impl: call engine.download() here and emit Downloading(pct) events.
            val tokenLimit = 0 // TODO: replace with engine.getTokenLimit() when available
            Timber.tag(TAG).d("initNanoFlow: Nano ready, tokenLimit=$tokenLimit")
            emit(NanoInitResult.Success(tokenLimit))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "initNanoFlow: init failed")
            emit(NanoInitResult.Failed(e.message ?: "Unknown error during Nano init"))
        }
    }

    // ── Token count gate (used by OutfitCoherenceScorer) ─────────────────────

    /**
     * Counts the number of tokens in [prompt] using the on-device Nano engine.
     *
     * Delegates to [NanoInferenceEngine.countTokens]. The stub returns [Int.MAX_VALUE]
     * so the gate in [OutfitCoherenceScorer] never trims the payload until the real
     * MLKit implementation is wired.
     *
     * Note: this method is intentionally exposed on [NanoProvider] (not on [OutfitAiProvider])
     * because token counting is Nano-specific. The coherence scorer casts to [NanoProvider]
     * only when the selected provider is [AiProvider.Nano].
     */
    suspend fun countTokens(prompt: String): Int = engine.countTokens(prompt)

    // ── OutfitAiProvider ──────────────────────────────────────────────────────

    override suspend fun selectOutfits(
        combos: List<OutfitComboPayload>,
        styleVibe: String,
    ): Result<List<OutfitSelection>> {
        if (combos.isEmpty()) {
            return Result.failure(IllegalArgumentException("Combo list is empty"))
        }

        if (!engine.isAvailable()) {
            return Result.failure(
                UnsupportedOperationException("Gemini Nano is not available on this device")
            )
        }

        return runCatching {
            val comboJson = buildComboJson(combos)
            val prompt = "$SYSTEM_PROMPT\n\nStyle vibe: $styleVibe\n\nCombos:\n$comboJson"

            val responseText = engine.generate(prompt)
            parseResponse(responseText, combos)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "NanoProvider inference failed")
        }
    }

    /**
     * Serializes the combo list to compact JSON for the dynamic prompt suffix.
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

    /** Wraps a string in JSON double-quotes, escaping internal quotes and backslashes. */
    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    /**
     * Parses the model's JSON response into a list of [OutfitSelection].
     *
     * Expected format: a top-level JSON array of objects, each with `combo_id` (Int)
     * and `reason` (String). Validates that all returned combo_ids exist in [combos].
     * Throws [IllegalStateException] if fewer than 3 valid selections are returned.
     * Callers catch via [runCatching].
     */
    private fun parseResponse(responseText: String, combos: List<OutfitComboPayload>): List<OutfitSelection> {
        val validComboIds = combos.map { it.comboId }.toSet()
        val array = json.parseToJsonElement(responseText.trim()).jsonArray
        val selections = array.mapNotNull { element ->
            val obj = element.jsonObject
            val comboId = obj["combo_id"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val reason = obj["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (comboId !in validComboIds) {
                Timber.tag(TAG).w("Nano returned combo_id=%d not in input — discarding", comboId)
                return@mapNotNull null
            }
            OutfitSelection(comboId = comboId, reason = reason)
        }
        check(selections.size >= 3) {
            "Nano returned only ${selections.size} valid selections — need at least 3"
        }
        return selections.take(3)
    }
}
