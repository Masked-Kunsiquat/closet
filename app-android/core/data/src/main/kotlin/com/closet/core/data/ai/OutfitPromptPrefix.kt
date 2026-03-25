package com.closet.core.data.ai

/**
 * Shared system prompt for all [OutfitAiProvider] implementations.
 *
 * Extracted from [NanoProvider] and [OpenAiProvider] so both providers use exactly
 * the same instructions — any change to the prompt is made in one place.
 *
 * Content:
 * - Role assignment: outfit coherence scorer
 * - Selection constraint: only select IDs from the given candidate list
 * - Validity rule: (Top + Bottom) OR (OnePiece), optional Outerwear/Footwear/Accessory
 * - Heuristics: prefer color harmony, avoid pattern clashes, weight suitability scores
 * - Response format: strict JSON, no preamble
 */
object OutfitPromptPrefix {
    val SYSTEM_PROMPT: String = """
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
