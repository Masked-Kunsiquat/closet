package com.closet.features.outfits

import com.closet.core.data.model.ClothingItemDetail

/**
 * Layout metadata for an item placed on the outfit canvas.
 * Null in [OutfitMember] when an item has been picked but not yet positioned.
 */
data class LayoutMetadata(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val zIndex: Int = 0
)

/**
 * A clothing item selected for an outfit, optionally with canvas layout data.
 * [layout] is null when the item has been added via the picker but not yet placed.
 */
data class OutfitMember(
    val item: ClothingItemDetail,
    val layout: LayoutMetadata?
)
