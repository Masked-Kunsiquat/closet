package com.closet.features.recommendations.ai

import com.closet.core.data.ai.NanoInitResult
import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.core.data.ai.OutfitComboPayload
import com.closet.core.data.ai.OutfitSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor stub for [NanoProvider].
 *
 * Google Play Services and the MLKit GenAI Prompt API are not included in the
 * FOSS build. This stub satisfies the [OutfitAiProvider] and [NanoInitializer]
 * bindings so the rest of the codebase compiles unchanged.
 *
 * - [initNanoFlow] immediately emits [NanoInitResult.NotSupported], which causes
 *   [com.closet.features.settings.SettingsViewModel] to disable the AI toggle
 *   and show "Not supported on this device" — the same path a real unsupported
 *   device takes.
 * - [selectOutfits] returns failure, causing [OutfitCoherenceScorer] to fall
 *   back to the programmatic top-3 result unchanged.
 * - [countTokens] returns [Int.MAX_VALUE] — the sentinel that keeps the token-trim
 *   gate in [OutfitCoherenceScorer] inactive, matching the full-flavor behaviour
 *   on devices where the beta countTokens API is not yet exposed.
 */
@Singleton
class NanoProvider @Inject constructor() : OutfitAiProvider, NanoInitializer {

    override fun initNanoFlow(): Flow<NanoInitResult> =
        flowOf(NanoInitResult.NotSupported)

    override suspend fun selectOutfits(
        combos: List<OutfitComboPayload>,
        styleVibe: String,
    ): Result<List<OutfitSelection>> = Result.failure(
        UnsupportedOperationException("On-device AI is not available in the FOSS build")
    )

    /** Returns [Int.MAX_VALUE] so the token-trim gate in [OutfitCoherenceScorer] never fires. */
    suspend fun countTokens(prompt: String): Int = Int.MAX_VALUE
}
