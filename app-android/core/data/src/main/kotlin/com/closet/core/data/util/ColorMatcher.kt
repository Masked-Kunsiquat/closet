package com.closet.core.data.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.closet.core.data.model.ColorEntity
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility to match an extracted color to the nearest color in a predefined palette.
 */
object ColorMatcher {

    /**
     * Finds the nearest color in the given palette using HSL distance formula.
     * Formula: sqrt(2 * ΔH² + ΔS² + ΔL²) where Hue is weighted 2x.
     */
    fun findNearestColor(extractedRgb: Int, palette: List<ColorEntity>): ColorEntity {
        require(palette.isNotEmpty()) { "Palette cannot be empty" }

        val extractedHsl = FloatArray(3)
        ColorUtils.colorToHSL(extractedRgb, extractedHsl)

        return palette.minBy { colorEntity ->
            val colorInt = parseHexColor(colorEntity.hex)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(colorInt, hsl)
            calculateHslDistance(extractedHsl, hsl)
        }
    }

    private fun parseHexColor(hex: String?): Int {
        if (hex.isNullOrBlank()) return Color.BLACK
        return try {
            val formattedHex = if (hex.startsWith("#")) hex else "#$hex"
            formattedHex.toColorInt()
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }
    }

    private fun calculateHslDistance(hsl1: FloatArray, hsl2: FloatArray): Double {
        // hsl[0] is Hue (0..360), hsl[1] is Saturation (0..1), hsl[2] is Lightness (0..1)
        // Normalize Hue to 0..1 to keep it in the same scale as S and L
        val h1 = hsl1[0] / 360f
        val h2 = hsl2[0] / 360f
        val s1 = hsl1[1]
        val s2 = hsl2[1]
        val l1 = hsl1[2]
        val l2 = hsl2[2]

        // Shortest distance on the hue circle (0.0 to 0.5)
        val deltaH = abs(h1 - h2).let { if (it > 0.5f) 1f - it else it }.toDouble()
        val deltaS = (s1 - s2).toDouble()
        val deltaL = (l1 - l2).toDouble()

        // Weighted distance formula: sqrt(2 * ΔH² + ΔS² + ΔL²)
        return sqrt(2 * deltaH.pow(2.0) + deltaS.pow(2.0) + deltaL.pow(2.0))
    }
}
