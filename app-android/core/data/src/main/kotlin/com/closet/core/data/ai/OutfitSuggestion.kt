package com.closet.core.data.ai

/**
 * Response returned by an [OutfitAiProvider].
 *
 * The provider selects item IDs from the candidate list it was given; it never generates
 * new clothing data. Callers must validate [selectedIds] against the original candidate
 * list before performing any DB interaction.
 *
 * @property selectedIds  The subset of candidate item IDs chosen by the model for this outfit.
 *                        Guaranteed non-empty on a successful result. Any IDs not present in
 *                        the original candidate list must be discarded before use.
 * @property reason       Optional explanation from the model for why this combination was
 *                        chosen. Surfaced behind a "why?" affordance in the UI — hidden by
 *                        default. May be null or blank if the model did not produce one.
 */
data class OutfitSuggestion(
    val selectedIds: List<Long>,
    val reason: String?,
)
