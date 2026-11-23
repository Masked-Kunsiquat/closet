package com.closet.features.recommendations.engine

import com.closet.core.data.color.Lab
import com.closet.core.data.color.Oklab
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * An outfit's color harmony split in two (both geometric means over the pairs of items that have
 * a [HarmonyColor]):
 * - [hueMultiplier]: the pairs scored by the hue bands ([ColorHarmony.hueMultiplier]);
 * - [familyMultiplier]: the same-family pairs whose family rule beat their hue term.
 *
 * [combined] is the engine's color harmony multiplier; the taste-weight features read the two
 * halves separately.
 */
internal data class ColorHarmonyFactors(
    val hueMultiplier: Double,
    val familyMultiplier: Double,
) {
    val combined: Double get() = hueMultiplier * familyMultiplier
}

/**
 * The color an item takes part in harmony with: its largest-share non-neutral tag.
 *
 * @property wheelHue  the tag's OKLCh hue on the painter's wheel ([ColorWheel.warp]), in [0, 360).
 * @property chroma    the tag's OKLCh chroma (at least [ColorHarmony.C_FLOOR]).
 * @property lightness the tag's Oklab L, in [0, 1].
 * @property family    the tag's `color_family`.
 * @property share     the tag's share of the item, in [0, 1] (`1 / n` when any share is missing).
 */
internal data class HarmonyColor(
    val wheelHue: Double,
    val chroma: Double,
    val lightness: Double,
    val family: String,
    val share: Double,
)

/**
 * Color harmony on measured shades (color v2 PR 2, #81). The one implementation behind
 * [OutfitRecommendationEngine]'s color multiplier, [PairScorer.hueTerm] and the taste-weight
 * features.
 *
 * - **Neutral tags.** A tag is neutral when it wears as neutral (`colors.wears_as_neutral`) or its
 *   chroma is below [C_FLOOR], where hue means nothing. Its family does not matter.
 * - **Harmony color.** Each item is judged by one color, [harmonyColor]. An item with none is
 *   neutral and sits out of harmony.
 * - **Pairs.** Each pair of harmony colors gets the hue band of their gap on the painter's wheel
 *   ([hueMultiplier]); a same-family pair takes [familyRule] instead when that is larger.
 * - **Shares.** A pair's log term is weighted by the product of the two colors' shares, so a 30%
 *   accent counts to the power 0.3.
 */
internal object ColorHarmony {

    /** Chroma below this (1.5 JND in Oklab) has no meaningful hue. */
    const val C_FLOOR = 0.03

    private const val ANALOGOUS_MAX_DEG = 60.0
    private const val COMPLEMENTARY_MIN_DEG = 150.0
    private const val MULTIPLIER_HUE_HARMONY = 1.05

    /**
     * The harmony color of [item]: its largest-share non-neutral tag, ties going to the earlier tag
     * (the DAO orders tags by color id). Null when every tag is neutral or unparseable.
     *
     * Each tag reads its [ColorHex.hex] (the measured hex, or the name hex) and, when that does not
     * parse, its [ColorHex.fallbackHex]; a tag with neither is skipped.
     */
    fun harmonyColor(item: EngineItem): HarmonyColor? {
        val tags = item.colorHexes
        if (tags.isEmpty()) return null
        val shares = sharesOf(tags)
        var best: HarmonyColor? = null
        for (i in tags.indices) {
            val tag = tags[i]
            if (tag.wearsAsNeutral) continue
            val lab = parseLab(tag.hex) ?: parseLab(tag.fallbackHex) ?: continue
            val chroma = Oklab.chroma(lab)
            if (chroma < C_FLOOR) continue
            val share = shares[i]
            if (best == null || share > best.share) {
                best = HarmonyColor(
                    wheelHue = ColorWheel.warp(Oklab.hueDegrees(lab)),
                    chroma = chroma,
                    lightness = lab.l,
                    family = tag.family,
                    share = share,
                )
            }
        }
        return best
    }

    /**
     * The hue band of a folded wheel [gap] (degrees, 0 to 180): analogous (≤ 60°) or
     * complementary (≥ 150°) × 1.05, anything else 1.0. There is no clash band.
     */
    fun hueMultiplier(gap: Double): Double = when {
        gap <= ANALOGOUS_MAX_DEG -> MULTIPLIER_HUE_HARMONY
        gap >= COMPLEMENTARY_MIN_DEG -> MULTIPLIER_HUE_HARMONY
        else -> 1.0
    }

    /**
     * The coarse color-family rule for a pair of families. Bonuses are additive on 1.0; Bright +
     * Bright is a clash (× 0.70) and takes priority over the same-family bonus (+0.10).
     *
     * In [factors] only the same-family branch can win over the hue term, because a pair of
     * different families always uses the hue term. Harmony is therefore a bonus only, in
     * [1.0, 1.10]; the clash constants stay as they are (D7).
     */
    fun familyRule(a: String, b: String): Double {
        val pair = setOf(a, b)
        return when {
            a == OutfitRecommendationEngine.FAMILY_BRIGHT && b == OutfitRecommendationEngine.FAMILY_BRIGHT ->
                OutfitRecommendationEngine.MULTIPLIER_BRIGHT_BRIGHT
            pair == setOf(OutfitRecommendationEngine.FAMILY_WARM, OutfitRecommendationEngine.FAMILY_COOL) ->
                OutfitRecommendationEngine.MULTIPLIER_WARM_COOL
            pair == setOf(OutfitRecommendationEngine.FAMILY_EARTH, OutfitRecommendationEngine.FAMILY_WARM) ->
                1.0 + OutfitRecommendationEngine.BONUS_EARTH_WARM
            a == b -> 1.0 + OutfitRecommendationEngine.BONUS_SAME_FAMILY
            else -> 1.0
        }
    }

    /**
     * The outfit's color harmony over every pair of items with a [harmonyColor] (D3, D4). Per pair,
     * with `w = sA × sB`: a different-family pair, or a same-family pair whose hue term is at least
     * its family rule, adds `w · ln(hue)` to the hue side; otherwise `w · ln(familyRule)` goes to
     * the family side. Each side is `exp(sum / pairCount)`. With fewer than two harmony colors both
     * are 1.0.
     */
    fun factors(items: List<EngineItem>): ColorHarmonyFactors {
        val colors = items.mapNotNull { it.harmony }
        if (colors.size < 2) return NO_EFFECT
        var hueLog = 0.0
        var familyLog = 0.0
        var pairCount = 0
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                val a = colors[i]
                val b = colors[j]
                val hue = hueMultiplier(ColorWheel.gap(a.wheelHue, b.wheelHue))
                val family = familyRule(a.family, b.family)
                val weight = a.share * b.share
                if (a.family != b.family || hue >= family) {
                    hueLog += weight * ln(hue)
                } else {
                    familyLog += weight * ln(family)
                }
                pairCount++
            }
        }
        return ColorHarmonyFactors(
            hueMultiplier = exp(hueLog / pairCount),
            familyMultiplier = exp(familyLog / pairCount),
        )
    }

    /**
     * Learned feature: the mean `|ΔC|` over every pair of harmony colors, divided by
     * [FEATURE_SCALE]. 0.0 with fewer than two harmony colors. It only matters once its weight is
     * learned away from 1.0.
     */
    fun chromaMatchFeature(items: List<EngineItem>): Double {
        val colors = items.mapNotNull { it.harmony }
        var sum = 0.0
        var pairCount = 0
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                sum += abs(colors[i].chroma - colors[j].chroma)
                pairCount++
            }
        }
        return if (pairCount == 0) 0.0 else sum / pairCount / FEATURE_SCALE
    }

    /**
     * Learned feature: the mean `|ΔL|`, divided by [FEATURE_SCALE], over the harmony-color pairs
     * whose warped wheel gap is at most 60 degrees. 0.0 when no pair qualifies.
     */
    fun lightnessSpreadFeature(items: List<EngineItem>): Double {
        val colors = items.mapNotNull { it.harmony }
        var sum = 0.0
        var pairCount = 0
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                if (ColorWheel.gap(colors[i].wheelHue, colors[j].wheelHue) > ANALOGOUS_MAX_DEG) continue
                sum += abs(colors[i].lightness - colors[j].lightness)
                pairCount++
            }
        }
        return if (pairCount == 0) 0.0 else sum / pairCount / FEATURE_SCALE
    }

    private const val FEATURE_SCALE = 0.1

    private val NO_EFFECT = ColorHarmonyFactors(1.0, 1.0)

    /**
     * Each tag's share clamped to [0, 1]. When any share is missing (a manual tag) or NaN, every
     * tag counts `1 / n`.
     */
    private fun sharesOf(tags: List<ColorHex>): DoubleArray {
        val raw = tags.map { it.share }
        if (raw.any { it == null || it.isNaN() }) return DoubleArray(tags.size) { 1.0 / tags.size }
        return DoubleArray(tags.size) { raw[it]!!.coerceIn(0.0, 1.0) }
    }

    /**
     * [hex] as Oklab, or null when it is not `#RRGGBB` or `#AARRGGBB` (alpha ignored), after
     * trimming and ignoring case. Never throws.
     */
    private fun parseLab(hex: String?): Lab? {
        if (hex == null) return null
        val text = hex.trim().uppercase()
        if (!text.startsWith("#")) return null
        val digits = text.substring(1)
        if (digits.length != 6 && digits.length != 8) return null
        if (!digits.all { it in '0'..'9' || it in 'A'..'F' }) return null
        val rgb = if (digits.length == 8) digits.substring(2) else digits
        return try {
            Oklab.fromHex("#$rgb")
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
