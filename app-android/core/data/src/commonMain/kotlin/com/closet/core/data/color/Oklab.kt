package com.closet.core.data.color

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A colour in Oklab space. */
data class Lab(val l: Double, val a: Double, val b: Double)

/** Pure-Kotlin sRGB and Oklab conversions. */
object Oklab {
    fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun linearToSrgb(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    /** Linear-light [r, g, b] in 0..1 from an ARGB int (alpha ignored). */
    fun linearRgb(argb: Int): DoubleArray = doubleArrayOf(
        srgbToLinear(((argb shr 16) and 0xFF) / 255.0),
        srgbToLinear(((argb shr 8) and 0xFF) / 255.0),
        srgbToLinear((argb and 0xFF) / 255.0),
    )

    fun fromArgb(argb: Int): Lab {
        val c = linearRgb(argb)
        return fromLinear(c[0], c[1], c[2])
    }

    /** Accepts `#RRGGBB` (case-insensitive); throws [IllegalArgumentException] otherwise. */
    fun fromHex(hex: String): Lab {
        require(hex.length == 7 && hex[0] == '#') { "Expected #RRGGBB but was '$hex'" }
        val rgb = hex.substring(1).toIntOrNull(16)
            ?: throw IllegalArgumentException("Expected #RRGGBB but was '$hex'")
        require(hex.substring(1).all { it.digitToIntOrNull(16) != null }) { "Expected #RRGGBB but was '$hex'" }
        return fromArgb(rgb or (0xFF shl 24))
    }

    /** Oklab from linear-light r, g, b in 0..1. */
    internal fun fromLinear(r: Double, g: Double, b: Double): Lab {
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Lab(
            l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** Clamps to [0, 1] and returns uppercase `#RRGGBB`. */
    fun hexFromLinear(r: Double, g: Double, b: Double): String {
        fun byte(c: Double): Int =
            (linearToSrgb(c.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
        fun two(v: Int): String = v.toString(16).uppercase().padStart(2, '0')
        return "#" + two(byte(r)) + two(byte(g)) + two(byte(b))
    }

    fun deltaE(x: Lab, y: Lab): Double {
        val dl = x.l - y.l
        val da = x.a - y.a
        val db = x.b - y.b
        return sqrt(dl * dl + da * da + db * db)
    }

    fun chroma(x: Lab): Double = sqrt(x.a * x.a + x.b * x.b)

    /** Hue angle in degrees, in [0, 360). */
    fun hueDegrees(x: Lab): Double {
        val deg = atan2(x.b, x.a) * 180.0 / kotlin.math.PI
        val h = if (deg < 0) deg + 360.0 else deg
        return if (h >= 360.0) 0.0 else h
    }
}
