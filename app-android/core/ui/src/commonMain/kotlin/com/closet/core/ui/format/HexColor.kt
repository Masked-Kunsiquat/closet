package com.closet.core.ui.format

import androidx.compose.ui.graphics.Color

/**
 * Parses a `#RRGGBB` or `#AARRGGBB` hex string (case-insensitive) into a [Color].
 * Returns null for anything else, so call sites can supply their own fallback.
 */
fun parseHexColor(hex: String?): Color? {
    if (hex == null || !hex.startsWith('#')) return null
    val digits = hex.substring(1)
    if (digits.length != 6 && digits.length != 8) return null
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val value = digits.toLong(radix = 16)
    val argb = if (digits.length == 6) value or 0xFF000000L else value
    return Color(argb)
}
