package com.closet.tools.demobackup

import com.closet.core.data.DatabaseSeeder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * BASELINE ONLY. A faithful copy of the deleted app matcher (`ColorMatcher`): weighted HSL distance
 * `sqrt(2 * dH^2 + dS^2 + dL^2)` against the legacy base `hex` of the 22 seeded colours, with hue
 * normalised to 0..1 and taking the shorter way round the circle. It exists so `color-check` can
 * report how the old matching rule would have named the same pixels. Nothing in the app uses it.
 */
object LegacyHslMatcher {

    private class Hsl(val h: Double, val s: Double, val l: Double)

    private val seeds: List<Pair<String, Hsl>> =
        DatabaseSeeder.SEEDED_COLORS.map { it.name to hsl(it.hex) }

    /** The seeded colour name nearest to [hex] (`#RRGGBB`); ties go to the earlier seed. */
    fun nearestName(hex: String): String {
        val target = hsl(hex)
        var best = seeds.first().first
        var bestD = Double.MAX_VALUE
        for ((name, c) in seeds) {
            val dh = hueDistance(target.h, c.h)
            val ds = target.s - c.s
            val dl = target.l - c.l
            val d = sqrt(2.0 * dh * dh + ds * ds + dl * dl)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b)
        return min(d, 1.0 - d)
    }

    private fun hsl(hex: String): Hsl {
        val v = hex.removePrefix("#").toInt(16)
        val r = ((v shr 16) and 0xFF) / 255.0
        val g = ((v shr 8) and 0xFF) / 255.0
        val b = (v and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2.0
        val delta = mx - mn
        if (delta == 0.0) return Hsl(0.0, 0.0, l)
        val s = delta / (1.0 - abs(2.0 * l - 1.0))
        val h6 = when (mx) {
            r -> ((g - b) / delta).mod(6.0)
            g -> (b - r) / delta + 2.0
            else -> (r - g) / delta + 4.0
        }
        return Hsl(h6 / 6.0, s, l)
    }
}
