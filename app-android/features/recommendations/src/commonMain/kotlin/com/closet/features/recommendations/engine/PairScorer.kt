package com.closet.features.recommendations.engine

import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.FORMALITY_GAP_PENALTY
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.K
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.PAIR_MAX
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.PAIR_MIN
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.W_MAX
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A pair's co-wear history: [lift] (`hist_ab`, in [PAIR_MIN, PAIR_MAX]; 1.0 with no evidence) and
 * the [weight] (`w_ab`, at most [W_MAX]; 0.0 with no evidence) it earns in the outfit blend.
 */
internal data class PairHistory(val lift: Double, val weight: Double)

/**
 * Pure pairwise compatibility terms for [OutfitRecommendationEngine]'s outfit score (engine v2,
 * #96). No I/O, no clock; the constants live in the engine companion.
 */
internal object PairScorer {

    private val NO_EVIDENCE = PairHistory(lift = 1.0, weight = 0.0)

    // Hue harmony thresholds (degrees on the hue circle, 0 to 180 after folding). The clash band
    // is fitted to the seeded palette: same-family seeded pairs sit at up to 77.4 degrees (Sky Blue
    // with Forest Green or Mint) and from 102.6 degrees (Sky Blue with Purple), so 80 to 100 never
    // marks a same-family seeded pair as a clash (PairScorerTest checks every one).
    private const val ANALOGOUS_MAX_DEG = 30.0
    private const val COMPLEMENTARY_MIN_DEG = 150.0
    private const val CLASH_MIN_DEG = 80.0
    private const val CLASH_MAX_DEG = 100.0
    private const val FAMILY_NEUTRAL = "Neutral"
    private const val MULTIPLIER_HUE_HARMONY = 1.05
    private const val MULTIPLIER_HUE_CLASH = 0.90

    // A color is chromatic (has a meaningful hue) only inside these HSL bounds.
    private const val MIN_CHROMATIC_SATURATION = 0.15
    private const val MIN_CHROMATIC_LIGHTNESS = 0.12
    private const val MAX_CHROMATIC_LIGHTNESS = 0.88

    /**
     * Co-wear history of items [a] and [b]. With `c` the weighted count of logs/outfits holding
     * both, `E = nA·nB / N` the chance expectation, `lift = clamp((c + 1) / (E + 1))` and
     * `weight = min(W_MAX, n / (n + K))` for `n = max(c, E)`. Using `max(c, E)` lets a pair that
     * is expected together often but never worn together earn weight (lift below 1).
     */
    fun history(a: Long, b: Long, coWear: CoWearStats): PairHistory {
        val c = coWear.pairCounts[if (a < b) a to b else b to a] ?: 0.0
        val nA = coWear.itemCounts[a] ?: 0.0
        val nB = coWear.itemCounts[b] ?: 0.0
        val expected = if (coWear.total > 0.0) nA * nB / coWear.total else 0.0
        val n = max(c, expected)
        if (n <= 0.0) return NO_EVIDENCE
        val lift = ((c + 1.0) / (expected + 1.0)).coerceIn(PAIR_MIN, PAIR_MAX)
        return PairHistory(lift = lift, weight = min(W_MAX, n / (n + K)))
    }

    /** × [FORMALITY_GAP_PENALTY] when both levels are known and differ by 2 or more, else 1.0. */
    fun formalityTerm(a: EngineItem, b: EngineItem): Double {
        val la = a.formalityLevel ?: return 1.0
        val lb = b.formalityLevel ?: return 1.0
        return if (abs(la - lb) >= 2.0) FORMALITY_GAP_PENALTY else 1.0
    }

    /**
     * Hue harmony of two items, each judged by [itemHue]: analogous (≤ 30°) or complementary
     * (150° to 210°) × 1.05, an 80° to 100° clash × 0.90, anything else 1.0. Null when either
     * item has no usable hue, so the caller uses the color-family rule.
     */
    fun hueTerm(a: EngineItem, b: EngineItem): Double? {
        val ha = itemHue(a) ?: return null
        val hb = itemHue(b) ?: return null
        val raw = abs(ha - hb) % 360.0
        val diff = if (raw > 180.0) 360.0 - raw else raw
        return when {
            diff <= ANALOGOUS_MAX_DEG -> MULTIPLIER_HUE_HARMONY
            diff >= COMPLEMENTARY_MIN_DEG -> MULTIPLIER_HUE_HARMONY
            diff in CLASH_MIN_DEG..CLASH_MAX_DEG -> MULTIPLIER_HUE_CLASH
            else -> 1.0
        }
    }

    /**
     * The hue of [item], from the same color its family side uses: the first chromatic hex (in
     * the DAO's color-id order) whose family is the item's dominant family ([dominantFamilyOf]),
     * then the first chromatic hex of any other non-Neutral family. Neutral-family hexes (such as
     * Navy) are never used. Null when there is none, or the dominant family is Neutral.
     */
    private fun itemHue(item: EngineItem): Double? {
        val dominant = dominantFamilyOf(item.colorFamilies)
        if (dominant == FAMILY_NEUTRAL) return null
        val nonNeutral = item.colorHexes.filter { it.family != FAMILY_NEUTRAL }
        val (preferred, others) = nonNeutral.partition { it.family == dominant }
        return chromaticHue(preferred) ?: chromaticHue(others)
    }

    /** The HSL hue (degrees) of the first parseable, chromatic hex in [colors], or null. */
    private fun chromaticHue(colors: List<ColorHex>): Double? {
        for (color in colors) {
            val rgb = parseRgb(color.hex) ?: continue
            val r = ((rgb shr 16) and 0xFF) / 255.0
            val g = ((rgb shr 8) and 0xFF) / 255.0
            val b = (rgb and 0xFF) / 255.0
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val lightness = (maxC + minC) / 2.0
            if (lightness < MIN_CHROMATIC_LIGHTNESS || lightness > MAX_CHROMATIC_LIGHTNESS) continue
            if (delta == 0.0) continue
            val saturation = delta / (1.0 - abs(2.0 * lightness - 1.0))
            if (saturation < MIN_CHROMATIC_SATURATION) continue
            val hue = when (maxC) {
                r -> 60.0 * (((g - b) / delta).mod(6.0))
                g -> 60.0 * ((b - r) / delta + 2.0)
                else -> 60.0 * ((r - g) / delta + 4.0)
            }
            return hue
        }
        return null
    }

    /** `#RRGGBB` or `#AARRGGBB` (alpha ignored) to a 24-bit RGB int, or null if malformed. */
    private fun parseRgb(hex: String): Int? {
        val digits = hex.trim().removePrefix("#")
        val rgb = when (digits.length) {
            6 -> digits
            8 -> digits.substring(2)
            else -> return null
        }
        if (!rgb.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return rgb.toInt(16)
    }
}
