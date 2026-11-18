package com.closet.features.recommendations.engine

import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.ALPHA_NEIGHBORS
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.BETA_SUBCATEGORY
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.FORMALITY_GAP_PENALTY
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.K
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.MIN_NEIGHBOR_SIMILARITY
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.PAIR_MAX
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.PAIR_MIN
import com.closet.features.recommendations.engine.OutfitRecommendationEngine.Companion.TOP_K_NEIGHBORS
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
     * Co-wear history of items [a] and [b] with two-level backoff (engine v2, Delivery 3, #96).
     *
     * 1. Direct co-wear (Level 0): observed count [c] vs chance expectation [expected].
     *    If direct evidence n0 >= K (5.0), direct history is used immediately without backoff.
     * 2. Neighbor generalization (Level 1): if direct evidence is sparse (n0 < K), similar items
     *    (cosine similarity >= 0.75, top 5) paired together contribute similarity-weighted lift
     *    discounted by [ALPHA_NEIGHBORS] (0.6).
     * 3. Subcategory fallback (Level 2): if evidence remains sparse, aggregate co-wear between
     *    the items' subcategories across the wardrobe contributes coarse baseline lift
     *    discounted by [BETA_SUBCATEGORY] (0.25, capped at K).
     */
    fun history(
        a: EngineItem,
        b: EngineItem,
        coWear: CoWearStats,
        itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
        subcategoryCoWear: CoWearStats = CoWearStats.EMPTY,
    ): PairHistory {
        val direct = directHistory(a.id, b.id, coWear)
        if (direct.evidence >= K) {
            val lift = direct.lift.coerceIn(PAIR_MIN, PAIR_MAX)
            return PairHistory(lift = lift, weight = min(W_MAX, direct.evidence / (direct.evidence + K)))
        }

        val n0 = direct.evidence
        val lift0 = direct.lift

        // Level 1: Neighbor generalization
        var sumSimLift = 0.0
        var sumSim = 0.0
        var sumSimEvidence = 0.0

        val neighborsA = itemNeighbors[a.id].orEmpty().filter { it.similarity >= MIN_NEIGHBOR_SIMILARITY }.take(TOP_K_NEIGHBORS)
        val neighborsB = itemNeighbors[b.id].orEmpty().filter { it.similarity >= MIN_NEIGHBOR_SIMILARITY }.take(TOP_K_NEIGHBORS)

        val extendedA = listOf(ItemNeighbor(a.id, 1.0)) + neighborsA
        val extendedB = listOf(ItemNeighbor(b.id, 1.0)) + neighborsB

        val targetKey = if (a.id < b.id) a.id to b.id else b.id to a.id
        val seenPairs = mutableSetOf<Pair<Long, Long>>()

        for (na in extendedA) {
            for (nb in extendedB) {
                if (na.itemId == nb.itemId) continue
                val pairKey = if (na.itemId < nb.itemId) na.itemId to nb.itemId else nb.itemId to na.itemId
                if (pairKey == targetKey) continue
                if (!seenPairs.add(pairKey)) continue
                val pairHist = directHistory(na.itemId, nb.itemId, coWear)
                if (pairHist.evidence > 0.0) {
                    val sim = na.similarity * nb.similarity
                    sumSimLift += sim * pairHist.lift
                    sumSim += sim
                    sumSimEvidence += sim * pairHist.evidence
                }
            }
        }

        val lift1 = if (sumSim > 0.0) sumSimLift / sumSim else 1.0
        val n1 = ALPHA_NEIGHBORS * sumSimEvidence

        // Level 2: Subcategory fallback
        val subA = a.subcategoryId
        val subB = b.subcategoryId
        val (lift2, n2) = if (n0 + n1 < K && subA != null && subB != null && subA != subB) {
            val subHist = directHistory(subA, subB, subcategoryCoWear)
            if (subHist.evidence > 0.0) {
                subHist.lift to min(K, BETA_SUBCATEGORY * subHist.evidence)
            } else {
                1.0 to 0.0
            }
        } else {
            1.0 to 0.0
        }

        val nEff = n0 + n1 + n2
        if (nEff <= 0.0) return NO_EVIDENCE

        val combinedLift = ((n0 * lift0 + n1 * lift1 + n2 * lift2) / nEff).coerceIn(PAIR_MIN, PAIR_MAX)
        val weight = min(W_MAX, nEff / (nEff + K))
        return PairHistory(lift = combinedLift, weight = weight)
    }

    /**
     * Overload for ID-only call sites (direct co-wear only, no backoff).
     *
     * Co-wear history of items [a] and [b]. With `c` the weighted count of logs/outfits holding
     * both, `E = nA·nB / N` the chance expectation, `lift = clamp((c + 1) / (E + 1))` and
     * `weight = min(W_MAX, n / (n + K))` for `n = max(c, E)`. Using `max(c, E)` lets a pair that
     * is expected together often but never worn together earn weight (lift below 1).
     */
    fun history(a: Long, b: Long, coWear: CoWearStats): PairHistory {
        val direct = directHistory(a, b, coWear)
        if (direct.evidence <= 0.0) return NO_EVIDENCE
        return PairHistory(
            lift = direct.lift.coerceIn(PAIR_MIN, PAIR_MAX),
            weight = min(W_MAX, direct.evidence / (direct.evidence + K))
        )
    }

    private data class DirectEvidence(val lift: Double, val evidence: Double)

    private fun directHistory(a: Long, b: Long, coWear: CoWearStats): DirectEvidence {
        val c = coWear.pairCounts[if (a < b) a to b else b to a] ?: 0.0
        val nA = coWear.itemCounts[a] ?: 0.0
        val nB = coWear.itemCounts[b] ?: 0.0
        val expected = if (coWear.total > 0.0) nA * nB / coWear.total else 0.0
        val n = max(c, expected)
        if (n <= 0.0) return DirectEvidence(lift = 1.0, evidence = 0.0)
        val lift = ((c + 1.0) / (expected + 1.0)).coerceIn(PAIR_MIN, PAIR_MAX)
        return DirectEvidence(lift = lift, evidence = n)
    }

    /** × [FORMALITY_GAP_PENALTY] when both levels are known and differ by 2 or more, else 1.0. */
    fun formalityTerm(a: EngineItem, b: EngineItem): Double {
        val la = a.formalityLevel ?: return 1.0
        val lb = b.formalityLevel ?: return 1.0
        return if (abs(la - lb) >= 2.0) FORMALITY_GAP_PENALTY else 1.0
    }

    /**
     * Hue harmony of two items, each judged by [itemHue]: analogous (angular distance ≤ 30°)
     * or complementary (folded distance ≥ 150°, representing 150° to 210° unfolded) × 1.05,
     * an 80° to 100° clash × 0.90, anything else 1.0. Null when either item has no usable hue,
     * so the caller uses the color-family rule.
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
        if (digits.length != 6 && digits.length != 8) return null
        if (!digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        val rgb = if (digits.length == 8) digits.substring(2) else digits
        return rgb.toInt(16)
    }
}
