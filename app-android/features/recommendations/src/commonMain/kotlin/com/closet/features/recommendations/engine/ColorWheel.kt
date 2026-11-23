package com.closet.features.recommendations.engine

import kotlin.math.abs

/**
 * The painter's (RYB) wheel the engine judges hue harmony on (color v2, #81). OKLCh hue is warped
 * piecewise-linearly through six anchors, so red and green, yellow and purple, and orange and blue
 * sit opposite each other the way they do on a painter's wheel:
 *
 * | Colour | OKLCh hue | Wheel |
 * |---|---|---|
 * | red | 29° | 0° |
 * | orange | 71° | 60° |
 * | yellow | 110° | 120° |
 * | green | 143° | 180° |
 * | blue | 266° | 240° |
 * | purple | 328° | 300° |
 *
 * The last segment runs from purple (328°) through 360° to red (29° + 360°), so the warp is
 * continuous across the wrap.
 */
internal object ColorWheel {

    // OKLCh hue to wheel position, with red repeated one turn on to close the circle.
    private val OKLCH = doubleArrayOf(29.0, 71.0, 110.0, 143.0, 266.0, 328.0, 389.0)
    private val WHEEL = doubleArrayOf(0.0, 60.0, 120.0, 180.0, 240.0, 300.0, 360.0)

    /** The wheel position of OKLCh hue [oklchHue] (degrees, any turn), in [0, 360). */
    fun warp(oklchHue: Double): Double {
        var h = oklchHue.mod(360.0)
        if (h < OKLCH.first()) h += 360.0
        for (i in 0 until OKLCH.size - 1) {
            val x0 = OKLCH[i]
            val x1 = OKLCH[i + 1]
            if (h <= x1) {
                val wheel = WHEEL[i] + (h - x0) / (x1 - x0) * (WHEEL[i + 1] - WHEEL[i])
                return if (wheel >= 360.0) wheel - 360.0 else wheel
            }
        }
        return 0.0 // unreachable: h is in [29, 389)
    }

    /** The angular distance between wheel positions [a] and [b], folded to [0, 180]. */
    fun gap(a: Double, b: Double): Double {
        val raw = abs(a - b).mod(360.0)
        return if (raw > 180.0) 360.0 - raw else raw
    }
}
