package com.closet.core.data.model

/**
 * One colour to write to an item's `clothing_item_colors` rows: the named [colorId], plus the
 * measured [hex] and pixel [share] when it came from extraction, and its [source] ([ColorSource]).
 */
data class TaggedColor(
    val colorId: Long,
    val hex: String? = null,
    val share: Double? = null,
    val source: String = ColorSource.USER,
)
