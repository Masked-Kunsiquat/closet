package com.closet.features.recommendations.ai

import com.closet.core.data.ai.ClothingItemDto
import com.closet.core.data.ai.OutfitPromptPrefix
import com.closet.core.data.ai.OutfitSuggestion
import com.closet.core.data.model.AiProvider
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitCombo
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates to the active [OutfitAiProvider] to re-rank the programmatic top-3
 * outfit candidates using AI coherence scoring.
 *
 * ### Role in the pipeline
 * The [OutfitRecommendationEngine] always runs first and produces the programmatic
 * top-3. This scorer is invoked *after* the engine, and optionally prepends an
 * AI-selected combo at position 0. It never replaces the engine — it augments its
 * output. If the scorer is unavailable, returns an error, or produces an invalid
 * response, the engine result is used as-is with no AI label.
 *
 * ### Provider selection
 * The active provider is determined at call time by reading
 * [AiPreferencesRepository.getSelectedProvider]. Readiness is checked per-provider:
 * - [AiProvider.Nano]      — [AiPreferencesRepository.getAiReady] must be true.
 * - [AiProvider.OpenAi]    — [AiPreferencesRepository.getOpenAiApiKey] must be non-blank.
 * - [AiProvider.Anthropic] — always returns null (not yet implemented).
 *
 * ### Token gate (Nano only)
 * Before calling [NanoProvider.suggestOutfit], the full prompt (system prefix +
 * candidate JSON) is counted via [NanoProvider.countTokens]. If the count exceeds
 * the stored token limit from [AiPreferencesRepository.getTokenLimit], candidates
 * are trimmed from the tail (lowest suitability score first) until the prompt fits.
 * If no candidates remain after trimming, the scorer returns null.
 *
 * [StubNanoInferenceEngine.countTokens] returns [Int.MAX_VALUE], so trimming never
 * fires until the real MLKit implementation is wired.
 *
 * ### Validation
 * [OutfitSuggestion.selectedIds] are validated against the original candidate ID set.
 * Any IDs not present in the candidate list are discarded. If no valid IDs remain
 * after filtering, the scorer returns null.
 *
 * ### Silent failure
 * All failures ([Result.failure], JSON errors, empty valid IDs) return null — the
 * caller then uses the programmatic result unchanged.
 */
@Singleton
class OutfitCoherenceScorer @Inject constructor(
    private val nanoProvider: NanoProvider,
    private val openAiProvider: OpenAiProvider,
    private val aiPreferencesRepository: AiPreferencesRepository,
) {

    companion object {
        private const val TAG = "OutfitCoherenceScorer"
    }

    /**
     * Attempts AI coherence scoring on the engine's candidate pool.
     *
     * @param engineInput  The full engine input — provides the candidate item pool and
     *                     their attributes for [ClothingItemDto] serialization.
     * @param itemScores   Per-item suitability scores computed by the engine's scoring
     *                     step. Included as context hints in [ClothingItemDto.suitabilityScore].
     *                     Items absent from this map receive a neutral score of 1.0.
     * @return An [OutfitCombo] with [OutfitCombo.isAiSelected] = true if the scorer
     *         produced a valid result, or null if AI is unavailable / scoring failed.
     */
    suspend fun score(
        engineInput: EngineInput,
        itemScores: Map<Long, Double>,
    ): OutfitCombo? {
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

        // 3. Build ClothingItemDto list from engine candidates
        var candidates: List<ClothingItemDto> = engineInput.candidates.map { item ->
            item.toDto(suitabilityScore = itemScores[item.id] ?: 1.0)
        }

        if (candidates.isEmpty()) return null

        // 4. Nano-only: token count gate — trim if over limit
        if (provider == AiProvider.Nano) {
            candidates = trimCandidatesToTokenLimit(candidates)
            if (candidates.isEmpty()) {
                Timber.tag(TAG).w("All candidates trimmed by token gate — falling back")
                return null
            }
        }

        // 5. Call the active provider
        val result = activeProvider.suggestOutfit(candidates)
        if (result.isFailure) {
            Timber.tag(TAG).w(result.exceptionOrNull(), "AI provider returned failure — falling back")
            return null
        }

        val suggestion = result.getOrNull() ?: return null

        // 6. Validate selected_ids against the candidate list
        val candidateIdSet = candidates.map { it.id }.toSet()
        val validIds = suggestion.selectedIds.filter { it in candidateIdSet }
        if (validIds.isEmpty()) {
            Timber.tag(TAG).w(
                "AI returned IDs not in candidate list (raw=%s) — discarding",
                suggestion.selectedIds
            )
            return null
        }

        // 7. Build the OutfitCombo from valid selected IDs
        val itemById: Map<Long, EngineItem> = engineInput.candidates.associateBy { it.id }
        val selectedItems = validIds.mapNotNull { itemById[it] }
        if (selectedItems.isEmpty()) return null

        Timber.tag(TAG).d(
            "AI selected %d items (provider=%s, reason=%s)",
            selectedItems.size,
            provider.name,
            suggestion.reason,
        )

        return OutfitCombo(
            items = selectedItems,
            score = selectedItems.map { itemScores[it.id] ?: 1.0 }.average(),
            isAiSelected = true,
            reason = suggestion.reason,
        )
    }

    // -------------------------------------------------------------------------
    // Nano token gate
    // -------------------------------------------------------------------------

    /**
     * Trims the candidate list until the full prompt (system prefix + candidate JSON)
     * fits within the Nano model's token limit.
     *
     * Trim strategy: drop the lowest-scoring candidate (last in the list, which is
     * pre-sorted descending by suitability score) on each iteration.
     *
     * Returns the original list unchanged when the limit is 0 (DataStore not yet
     * populated) or when the stub engine returns [Int.MAX_VALUE].
     */
    private suspend fun trimCandidatesToTokenLimit(
        candidates: List<ClothingItemDto>,
    ): List<ClothingItemDto> {
        val tokenLimit = aiPreferencesRepository.getTokenLimit().first()
        // 0 means not yet populated (Nano not ready) — skip trimming
        if (tokenLimit <= 0) return candidates

        // Sort descending by suitability so we drop lowest-scoring items from the tail
        val sorted = candidates.sortedByDescending { it.suitabilityScore }.toMutableList()

        while (sorted.isNotEmpty()) {
            val candidateJson = buildCandidateJson(sorted)
            val fullPrompt = "${OutfitPromptPrefix.SYSTEM_PROMPT}\n\nCandidates:\n$candidateJson"
            val tokenCount = nanoProvider.countTokens(fullPrompt)
            if (tokenCount <= tokenLimit) break
            // Drop the lowest-scoring candidate (tail of descending-sorted list)
            sorted.removeAt(sorted.lastIndex)
            Timber.tag(TAG).d("Token gate: trimmed to %d candidates (count=%d, limit=%d)",
                sorted.size, tokenCount, tokenLimit)
        }

        return sorted
    }

    // -------------------------------------------------------------------------
    // Serialization helpers (mirrors NanoProvider / OpenAiProvider)
    // -------------------------------------------------------------------------

    /**
     * Serializes candidate list to compact JSON for token counting.
     * Must produce exactly the same output as the providers' own serialization so
     * the token count is accurate.
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

    private fun String.asJsonString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

// -------------------------------------------------------------------------
// EngineItem → ClothingItemDto mapping
// -------------------------------------------------------------------------

/**
 * Maps an [EngineItem] to the [ClothingItemDto] payload sent to the AI provider.
 *
 * [suitabilityScore] is the per-item score from the engine's scoring step, included
 * as a context hint so the model has statistical signal beyond raw item attributes.
 */
private fun EngineItem.toDto(suitabilityScore: Double): ClothingItemDto = ClothingItemDto(
    id = id,
    name = name,
    outfitRole = outfitRole,
    colorFamilies = colorFamilies,
    isPatternSolid = isPatternSolid,
    suitabilityScore = suitabilityScore,
)
