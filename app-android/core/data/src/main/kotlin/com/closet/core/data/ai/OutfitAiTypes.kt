package com.closet.core.data.ai

/**
 * A single combo payload sent to the AI — the AI references it by [comboId].
 *
 * @property comboId  Sequential index (0..N-1) assigned before sending to the provider.
 *                    The provider echoes this index back in each [OutfitSelection] so the
 *                    caller can look up the original [OutfitComboPayload] without relying
 *                    on positional ordering in the response.
 * @property items    The clothing items that form this combo, enriched with AI context hints.
 */
data class OutfitComboPayload(
    val comboId: Int,              // sequential index 0..N-1 assigned before sending
    val items: List<ClothingItemDto>,
)

/**
 * One selection returned by the AI — references a [comboId] from the payload.
 *
 * @property comboId  Index into the original [OutfitComboPayload] list. The caller validates
 *                    that this value is in range before mapping back to an [OutfitComboPayload].
 * @property reason   One-sentence explanation from the AI for why this combo was chosen.
 *                    Surfaced behind a "why?" affordance in the UI — hidden by default.
 */
data class OutfitSelection(
    val comboId: Int,
    val reason: String,            // 1-sentence explanation from the AI
)
