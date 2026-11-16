package com.closet.features.stats

import androidx.compose.ui.graphics.Color

/**
 * Parses a `#RRGGBB` or `#AARRGGBB` hex string (case-insensitive) into a [Color].
 * Returns null for anything else, so call sites can supply their own fallback.
 */
internal fun parseHexColor(hex: String?): Color? {
    if (hex == null || !hex.startsWith('#')) return null
    val digits = hex.substring(1)
    if (digits.length != 6 && digits.length != 8) return null
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val value = digits.toLong(radix = 16)
    val argb = if (digits.length == 6) value or 0xFF000000L else value
    return Color(argb)
}

/**
 * Fixed hue values (HSV degrees) for the per-category color palette.
 * Each category is assigned a slot by its position in the sorted category list.
 */
private val categoryBaseHues = listOf(210f, 20f, 100f, 270f, 345f, 45f, 175f, 290f)

/**
 * Generates [count] colors for a single category's subcategory segments.
 * Saturation steps from vivid (0.85) down to pastel (0.30) so the largest subcategory
 * (first segment) is most saturated and smaller ones fade toward pastel.
 */
internal fun subcategoryColors(categoryIndex: Int, count: Int): List<Color> {
    val hue = categoryBaseHues[categoryIndex % categoryBaseHues.size]
    return (0 until count).map { i ->
        val saturation = if (count == 1) 0.65f
        else 0.85f - (i.toFloat() / (count - 1)) * 0.55f
        Color.hsv(hue, saturation, 0.90f)
    }
}
