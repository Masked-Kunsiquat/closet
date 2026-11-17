package com.closet.features.stats

import androidx.compose.ui.graphics.Color

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
