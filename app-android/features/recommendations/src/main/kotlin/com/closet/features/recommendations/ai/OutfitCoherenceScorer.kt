package com.closet.features.recommendations.ai

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.ItemAiContextHint
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.util.DataResult
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitCombo
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates to the active [OutfitAiProvider] to curate 3 AI-selected outfit combos
 * from the programmatic candidate pool.
 *
 * ### Role in the pipeline
 * The [OutfitRecommendationEngine] always runs first and produces the full programmatic
 * combo list. This scorer receives that list and optionally replaces the top-3 with
 * AI-selected combos. It never replaces the engine — it augments its output. If the
 * scorer is unavailable, returns an error, or produces an invalid response, the engine
 * result is used as-is with no AI label.
 *
 * ### Provider selection
 * The active provider is determined at call time by reading
 * [AiPreferencesRepository.getSelectedProvider]. Readiness is checked per-provider:
 * - [AiProvider.Nano]      — [AiPreferencesRepository.getAiReady] must be true.
 * - [AiProvider.OpenAi]    — [AiPreferencesRepository.getOpenAiApiKey] must be non-blank.
 * - [AiProvider.Anthropic] — always returns null (not yet implemented).
 *
 * ### Token gate (Nano only)
 * Before calling [NanoProvider.selectOutfits], the full prompt (system prefix +
 * combo JSON) is counted via [NanoProvider.countTokens]. If the count exceeds
 * the stored token limit from [AiPreferencesRepository.getTokenLimit], combos
 * are trimmed from the tail (lowest average suitability score first) until the
 * prompt fits. If no combos remain after trimming, the scorer returns null.
 *
 * [StubNanoInferenceEngine.countTokens] returns [Int.MAX_VALUE], so trimming never
 * fires until the real MLKit implementation is wired.
 *
 * ### Validation
 * Returned [OutfitSelection.comboId] values are validated against the input combo
 * index range. Any out-of-range IDs are discarded. If fewer than 3 valid selections
 * remain, the scorer returns null.
 *
 * ### Silent failure
 * All failures ([Result.failure], JSON errors, insufficient valid selections) return
 * null — the caller then uses the programmatic result unchanged.
 */
@Singleton
class OutfitCoherenceScorer @Inject constructor(
    private val nanoProvider: NanoProvider,
    private val openAiProvider: OpenAiProvider,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val recommendationRepository: RecommendationRepository,
) {

    companion object {
        private const val TAG = "OutfitCoherenceScorer"
        /** Maximum number of combos to send to the AI in a single request. */
        private const val MAX_COMBOS = 25
    }

    /**
     * Attempts AI curation on the engine's combo pool.
     *
     * @param combos       Full list of [OutfitCombo]s from the engine (already ranked by score).
     *                     Up to [MAX_COMBOS] are sent to the AI; the rest are silently dropped.
     * @param engineInput  The full engine input — provides the candidate item pool for building
     *                     [ClothingItemDto] payloads.
     * @param itemScores   Per-item suitability scores computed by the engine's scoring step.
     *                     Included as context hints in [ClothingItemDto.suitabilityScore].
     *                     Items absent from this map receive a neutral score of 1.0.
     * @param styleVibe    The requested aesthetic e.g. "Smart Casual", "Streetwear". Passed to
     *                     the provider as the style filter instruction.
     * @return A list of exactly 3 [OutfitCombo]s with [OutfitCombo.isAiSelected] = true if the
     *         scorer produced a valid result, or null if AI is unavailable / scoring failed.
     */
    suspend fun score(
        combos: List<OutfitCombo>,
        engineInput: EngineInput,
        itemScores: Map<Long, Double>,
        styleVibe: String = "Smart Casual",
    ): List<OutfitCombo>? {
        // 1. Check master AI toggle
        val aiEnabled = aiPreferencesRepository.getAiEnabled().first()
        if (!aiEnabled) return null

        // 2. Determine active provider and check readiness
        val provider = aiPreferencesRepository.getSelectedProvider().first()
        val activeProvider = when (provider) {
            AiProvider.Nano -> {
                val ready = aiPreferencesRepository.getAiReady().first()
                if (!ready) {
                    Timber.tag(TAG).d("Nano not ready — falling back to programmatic result")
                    return null
                }
                nanoProvider
            }
            AiProvider.OpenAi -> {
                val key = aiPreferencesRepository.getOpenAiApiKey().first()
                if (key.isBlank()) {
                    Timber.tag(TAG).d("OpenAI key not set — falling back to programmatic result")
                    return null
                }
                openAiProvider
            }
            AiProvider.Anthropic -> {
                Timber.tag(TAG).d("Anthropic not yet implemented — falling back to programmatic result")
                return null
            }
        }

        // 3. Cap the combo pool at MAX_COMBOS
        val pool = combos.take(MAX_COMBOS)
        if (pool.isEmpty()) return null

        // 4. Fetch AI context hints for all items across all combos in the pool
        val allItemIds = pool.flatMap { combo -> combo.items.map { it.id } }.distinct()
        val contextHints: Map<Long, ItemAiContextHint> = when (val r = recommendationRepository.getAiContextHints(allItemIds)) {
            is DataResult.Success -> r.data
            else -> {
                Timber.tag(TAG).w("getAiContextHints failed — falling back to programmatic result")
                return null
            }
        }

        // 5. Build OutfitComboPayload list — each combo gets a sequential comboId
        var payloads: List<OutfitComboPayload> = pool.mapIndexed { index, combo ->
            OutfitComboPayload(
                comboId = index,
                items = combo.items.map { item ->
                    item.toDto(
                        suitabilityScore = itemScores[item.id] ?: 1.0,
                        contextHint = contextHints[item.id],
                    )
                }
            )
        }

        // 6. Nano-only: token count gate — trim combos if over limit
        if (provider == AiProvider.Nano) {
            payloads = trimCombosToTokenLimit(payloads, styleVibe)
            if (payloads.isEmpty()) {
                Timber.tag(TAG).w("All combos trimmed by token gate — falling back")
                return null
            }
        }

        // 7. Call the active provider
        val result = activeProvider.selectOutfits(payloads, styleVibe)
        if (result.isFailure) {
            Timber.tag(TAG).w(result.exceptionOrNull(), "AI provider returned failure — falling back")
            return null
        }

        val selections = result.getOrNull() ?: return null

        // 8. Validate combo_ids against the payload index range
        val validIdRange = payloads.map { it.comboId }.toSet()
        val validSelections = selections.filter { it.comboId in validIdRange }
        if (validSelections.size < 3) {
            Timber.tag(TAG).w(
                "AI returned only %d valid combo_ids (raw=%s) — discarding",
                validSelections.size,
                selections.map { it.comboId },
            )
            return null
        }

        // 9. Map the 3 selections back to OutfitCombo objects
        val comboByIndex: Map<Int, OutfitCombo> = pool.mapIndexed { index, combo -> index to combo }.toMap()
        val aiCombos = validSelections.take(3).mapNotNull { selection ->
            val original = comboByIndex[selection.comboId] ?: return@mapNotNull null
            original.copy(isAiSelected = true, reason = selection.reason)
        }
        if (aiCombos.size < 3) return null

        Timber.tag(TAG).d(
            "AI selected 3 combos (provider=%s, styleVibe=%s)",
            provider.name,
            styleVibe,
        )

        return aiCombos
    }

    // -------------------------------------------------------------------------
    // Nano token gate
    // -------------------------------------------------------------------------

    /**
     * Trims the combo list until the full prompt (system prefix + style vibe + combo JSON)
     * fits within the Nano model's token limit.
     *
     * Trim strategy: drop the lowest-scoring combo (last in the list, which is pre-sorted
     * descending by average item suitability) on each iteration.
     *
     * Returns the original list unchanged when the limit is 0 (DataStore not yet
     * populated) or when the stub engine returns [Int.MAX_VALUE].
     */
    private suspend fun trimCombosToTokenLimit(
        payloads: List<OutfitComboPayload>,
        styleVibe: String,
    ): List<OutfitComboPayload> {
        val tokenLimit = aiPreferencesRepository.getTokenLimit().first()
        // 0 means not yet populated (Nano not ready) — skip trimming
        if (tokenLimit <= 0) return payloads

        val sorted = payloads.toMutableList()

        while (sorted.isNotEmpty()) {
            val comboJson = buildComboJson(sorted)
            val fullPrompt = "${OutfitPromptPrefix.SYSTEM_PROMPT}\n\nStyle vibe: $styleVibe\n\nCombos:\n$comboJson"
            val tokenCount = nanoProvider.countTokens(fullPrompt)
            if (tokenCount <= tokenLimit) break
            sorted.removeAt(sorted.lastIndex)
            Timber.tag(TAG).d(
                "Token gate: trimmed to %d combos (count=%d, limit=%d)",
                sorted.size, tokenCount, tokenLimit,
            )
        }

        return sorted
    }

    // -------------------------------------------------------------------------
    // Serialization helpers (mirrors NanoProvider / OpenAiProvider)
    // -------------------------------------------------------------------------

    /**
     * Serializes combo list to compact JSON for token counting.
     * Must produce exactly the same output as the providers' own serialization so
     * the token count is accurate.
     */
    private fun buildComboJson(payloads: List<OutfitComboPayload>): String =
        buildString {
            append("[")
            payloads.forEachIndexed { i, combo ->
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
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

// -------------------------------------------------------------------------
// EngineItem → ClothingItemDto mapping
// -------------------------------------------------------------------------

/**
 * Maps an [EngineItem] to the [ClothingItemDto] payload sent to the AI provider.
 *
 * @param suitabilityScore  Per-item score from the engine's scoring step, included as a
 *                          context hint so the model has statistical signal beyond raw attributes.
 * @param contextHint       Optional AI enrichment context from [RecommendationRepository.getAiContextHints].
 *                          Provides human-readable subcategory name and primary material.
 *                          Null fields are passed through as null in the DTO.
 */
private fun EngineItem.toDto(
    suitabilityScore: Double,
    contextHint: ItemAiContextHint?,
): ClothingItemDto = ClothingItemDto(
    id = id,
    name = name,
    clothingType = contextHint?.subcategoryName,
    material = contextHint?.primaryMaterial,
    outfitRole = outfitRole,
    layer = warmthLayer,
    colorFamilies = colorFamilies,
    isPatternSolid = isPatternSolid,
    suitabilityScore = suitabilityScore,
)
