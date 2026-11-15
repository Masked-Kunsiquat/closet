package com.closet.core.data.ai

/**
 * Lightweight payload representation of a clothing item sent to an AI provider.
 *
 * Contains only the fields the model needs to make a coherence judgement; raw DB
 * entities are never serialized directly to avoid leaking unrelated data.
 *
 * @property id              Clothing item primary key — must be validated against the
 *                           candidate list before any DB interaction on the response side.
 * @property name            Display name.
 * @property clothingType    Subcategory name (e.g. "Button-down"), or null if uncategorised.
 * @property material        Primary material name (e.g. "Cotton"), or null if untagged.
 * @property outfitRole      Value of `categories.outfit_role`
 *                           (Top / Bottom / OnePiece / Outerwear / Footwear / Accessory / Other).
 *                           Null when the item has no category assigned.
 * @property layer           Value of `categories.warmth_layer`
 *                           (Base / Mid / Outer / None). Null when uncategorised.
 * @property colorFamilies   Set of color family strings for the item's tagged colors
 *                           (Neutral / Earth / Cool / Warm / Bright). Empty = treated as Neutral.
 * @property isPatternSolid  True when the item carries no pattern or only "Solid" patterns.
 * @property suitabilityScore Per-item suitability score from the programmatic pipeline
 *                            (0.0–1.0+). Included as a context hint so the model has
 *                            statistical signal beyond raw item attributes.
 */
data class ClothingItemDto(
    val id: Long,
    val name: String,
    val clothingType: String?,     // subcategory name e.g. "Button-down", null if uncategorised
    val material: String?,          // primary material e.g. "Cotton", null if untagged
    val outfitRole: String?,        // "Top", "Bottom", "OnePiece", etc.
    val layer: String?,             // "Base", "Mid", "Outer", "None" — from warmthLayer
    val colorFamilies: Set<String>,
    val isPatternSolid: Boolean,
    val suitabilityScore: Double,   // engine per-item score, used as guardrail hint
)
