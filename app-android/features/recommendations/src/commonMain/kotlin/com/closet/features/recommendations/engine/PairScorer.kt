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
        val neighborSims = mutableMapOf<Pair<Long, Long>, Double>()

        for (na in extendedA) {
            for (nb in extendedB) {
                if (na.itemId == nb.itemId) continue
                val pairKey = if (na.itemId < nb.itemId) na.itemId to nb.itemId else nb.itemId to na.itemId
                if (pairKey == targetKey) continue
                val sim = na.similarity * nb.similarity
                val prev = neighborSims[pairKey]
                if (prev == null || sim > prev) {
                    neighborSims[pairKey] = sim
                }
            }
        }

        for ((pairKey, sim) in neighborSims) {
            val pairHist = directHistory(pairKey.first, pairKey.second, coWear)
            if (pairHist.evidence > 0.0) {
                sumSimLift += sim * pairHist.lift
                sumSim += sim
                sumSimEvidence += sim * pairHist.evidence
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
     * Hue harmony of two items, each judged by its harmony color ([ColorHarmony.harmonyColor]):
     * the hue band ([ColorHarmony.hueMultiplier]) of their gap on the painter's wheel. Unweighted;
     * [ColorHarmony.factors] weights pairs by share. Null when either item has no harmony color.
     */
    fun hueTerm(a: EngineItem, b: EngineItem): Double? {
        val ca = a.harmony ?: return null
        val cb = b.harmony ?: return null
        return ColorHarmony.hueMultiplier(ColorWheel.gap(ca.wheelHue, cb.wheelHue))
    }
}
