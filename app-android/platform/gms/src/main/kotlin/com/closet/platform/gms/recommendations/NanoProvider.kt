package com.closet.platform.gms.recommendations

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OnDeviceAiException
import com.closet.core.data.ai.OutfitSelection
import com.closet.platform.gms.toOnDeviceAiException
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import timber.log.Timber

// ---------------------------------------------------------------------------
// Internal inference engine abstraction
// ---------------------------------------------------------------------------

/**
 * Internal abstraction over the MLKit GenAI Prompt API.
 * Isolates the ML Kit API surface from [NanoProvider].
 */
internal interface NanoInferenceEngine {
    /** Returns the current feature availability status from AICore. */
    suspend fun checkStatus(): Int

    /**
     * Starts (or attaches to an in-progress) model download.
     * Emits [DownloadStatus] events and completes on [DownloadStatus.DownloadCompleted]
     * or [DownloadStatus.DownloadFailed].
     */
    fun downloadFlow(): Flow<DownloadStatus>

    /**
     * Counts tokens in [prompt] with the same generation config as [generate]. Returns
     * [Int.MAX_VALUE] if counting fails, so the gate in
     * [com.closet.features.recommendations.ai.OutfitCoherenceScorer] doesn't trim the payload.
     */
    suspend fun countTokens(prompt: String): Int

    /** The model's input token limit (ML Kit `getTokenLimit()`). */
    suspend fun tokenLimit(): Int

    /**
     * Runs inference and returns the raw model output text.
     * Throws on model error or [com.google.mlkit.common.MlKitException].
     */
    suspend fun generate(prompt: String): String
}

/**
 * Real implementation backed by [Generation] (com.google.mlkit:genai-prompt).
 */
internal class MlKitNanoInferenceEngine : NanoInferenceEngine {

    private val model by lazy { Generation.getClient() }

    override suspend fun checkStatus(): Int = model.checkStatus()

    override fun downloadFlow(): Flow<DownloadStatus> = model.download()

    private fun request(prompt: String) = generateContentRequest(TextPart(prompt)) {
        temperature = 0.2f
        topK = 40
        maxOutputTokens = 512
    }

    override suspend fun countTokens(prompt: String): Int = try {
        model.countTokens(request(prompt)).totalTokens
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.tag("NanoInferenceEngine").w(e, "countTokens failed — trim gate disabled")
        Int.MAX_VALUE
    }

    override suspend fun tokenLimit(): Int = model.getTokenLimit()

    override suspend fun generate(prompt: String): String {
        val response = try {
            model.generateContent(request(prompt))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw e.toOnDeviceAiException()
        }
        return response.candidates.firstOrNull()?.text
            ?: throw OnDeviceAiException(OnDeviceAiException.Reason.FAILED)
    }
}

// ---------------------------------------------------------------------------
// NanoProvider
// ---------------------------------------------------------------------------

/**
 * [OutfitAiProvider] backed by the MLKit GenAI Prompt API (Gemini Nano on-device).
 *
 * No API key required. Runs entirely on-device via Android AICore. Available on
 * supported devices (Pixel 6+, Galaxy S24+, etc.) once the model is downloaded.
 * Callers must check [com.closet.core.data.repository.AiPreferencesRepository.getAiReady]
 * before invoking — this provider returns [Result.failure] immediately if Nano
 * is not [FeatureStatus.AVAILABLE].
 *
 * Token management (countTokens / trim payload) is performed by the coherence scorer
 * layer, not here. Init reports the real ML Kit getTokenLimit() in
 * [NanoInitResult.Success]; the settings screen persists it. A stored limit of 0 (Nano never
 * initialised, or getTokenLimit() failed) disables trimming in
 * [com.closet.features.recommendations.ai.OutfitCoherenceScorer].
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
 * Bound to [OutfitAiProvider] and [NanoInitializer] via
 * [com.closet.platform.gms.di.gmsModule].
 */
class NanoProvider(
    private val json: Json,
) : OnDeviceRecommendationProvider {

    private val engine: NanoInferenceEngine = MlKitNanoInferenceEngine()

    companion object {
        private const val TAG = "NanoProvider"
        private val SYSTEM_PROMPT get() = OutfitPromptPrefix.SYSTEM_PROMPT
    }

    // ── NanoInitializer ───────────────────────────────────────────────────────

    /**
     * Runs the Nano init sequence and emits progress + terminal result.
     *
     * Sequence:
     *   1. [NanoInferenceEngine.checkStatus] — gate
     *   2. If [FeatureStatus.UNAVAILABLE] → emit [NanoInitResult.NotSupported]
     *   3. If [FeatureStatus.AVAILABLE] → emit [NanoInitResult.Success]
     *   4. If [FeatureStatus.DOWNLOADABLE] or [FeatureStatus.DOWNLOADING] →
     *      collect [NanoInferenceEngine.downloadFlow], emit [NanoInitResult.Downloading]
     *      progress events, then emit [NanoInitResult.Success] or [NanoInitResult.Failed]
     */
    override fun initNanoFlow(): Flow<NanoInitResult> = flow {
        Timber.tag(TAG).d("initNanoFlow: checking Nano availability")
        val status = try {
            engine.checkStatus()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "initNanoFlow: checkStatus threw — AICore unavailable on this device")
            emit(NanoInitResult.NotSupported)
            return@flow
        }
        when (status) {
            FeatureStatus.UNAVAILABLE -> {
                Timber.tag(TAG).d("initNanoFlow: Nano not supported on this device")
                emit(NanoInitResult.NotSupported)
            }

            FeatureStatus.AVAILABLE -> {
                Timber.tag(TAG).d("initNanoFlow: Nano already available")
                emit(NanoInitResult.Success(tokenLimit = readTokenLimit()))
            }

            FeatureStatus.DOWNLOADABLE,
            FeatureStatus.DOWNLOADING -> {
                Timber.tag(TAG).d("initNanoFlow: starting Nano model download")
                try {
                    var totalDownloadBytes = 0L
                    engine.downloadFlow().collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadStarted -> {
                                Timber.tag(TAG).d("initNanoFlow: download started")
                                totalDownloadBytes = status.bytesToDownload
                                emit(NanoInitResult.Downloading(0))
                            }
                            is DownloadStatus.DownloadProgress -> {
                                val percent = if (totalDownloadBytes > 0L) {
                                    (status.totalBytesDownloaded * 100L / totalDownloadBytes)
                                        .toInt()
                                        .coerceAtMost(99)
                                } else {
                                    0
                                }
                                emit(NanoInitResult.Downloading(percent))
                            }
                            is DownloadStatus.DownloadCompleted -> {
                                Timber.tag(TAG).d("initNanoFlow: download complete")
                                emit(NanoInitResult.Success(tokenLimit = readTokenLimit()))
                            }
                            is DownloadStatus.DownloadFailed -> {
                                val reason = "Nano model download failed"
                                Timber.tag(TAG).w("initNanoFlow: $reason")
                                emit(NanoInitResult.Failed(reason))
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.tag(TAG).w(e, "initNanoFlow: download error")
                    emit(NanoInitResult.Failed(e.message ?: "Unknown error during Nano download"))
                }
            }
        }
    }

    // ── Token count gate (used by OutfitCoherenceScorer) ─────────────────────

    /**
     * Counts the number of tokens in [prompt] using the on-device Nano engine.
     * Returns [Int.MAX_VALUE] if counting fails, keeping the trim gate in
     * [com.closet.features.recommendations.ai.OutfitCoherenceScorer] inactive.
     */
    override suspend fun countTokens(prompt: String): Int = engine.countTokens(prompt)

    /** Real token limit, or 0 ("unknown", trim disabled) if ML Kit can't report it. */
    private suspend fun readTokenLimit(): Int = try {
        engine.tokenLimit()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.tag(TAG).w(e, "getTokenLimit failed — token trimming disabled")
        0
    }

    // ── OutfitAiProvider ──────────────────────────────────────────────────────

    override suspend fun selectOutfits(
        combos: List<OutfitComboPayload>,
        styleVibe: String,
    ): Result<List<OutfitSelection>> {
        if (combos.isEmpty()) {
            return Result.failure(IllegalArgumentException("Combo list is empty"))
        }

        val status = try {
            engine.checkStatus()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "selectOutfits: checkStatus threw — Nano unavailable")
            return Result.failure(e.toOnDeviceAiException())
        }
        if (status != FeatureStatus.AVAILABLE) {
            return Result.failure(OnDeviceAiException(OnDeviceAiException.Reason.NOT_AVAILABLE))
        }

        return try {
            val comboJson = buildComboJson(combos)
            val prompt = "$SYSTEM_PROMPT\n\nStyle vibe: $styleVibe\n\nCombos:\n$comboJson"
            val responseText = engine.generate(prompt)
            Result.success(parseResponse(responseText, combos))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w(e, "NanoProvider inference failed")
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\"", "\\\"")}\""

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
        val distinct = selections.distinctBy { it.comboId }
        check(distinct.size >= 3) {
            "Nano returned only ${distinct.size} distinct valid selections — need at least 3"
        }
        return distinct.take(3)
    }
}
