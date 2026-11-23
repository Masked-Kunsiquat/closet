package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.ColorHarmony
import com.closet.features.recommendations.engine.ColorHarmonyFactors
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.ItemNeighbor
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.PairScorer
import kotlin.math.ln
import kotlin.math.pow

/**
 * Pure feature extractor that maps a candidate (or partial) outfit into the 10-dimensional
 * log-feature vector $x \in \mathbb{R}^{10}$ consumed by [TasteWeights] and the conditional logit solver.
 */
internal object OutfitFeatureExtractor {

    /**
     * Extracts the 10-dimensional feature vector $x$ for [items]:
     * - $x_0 = \ln(\text{meanItem})$
     * - $x_1 = (1 - W) \ln(\text{hueMultiplier})$
     * - $x_2 = (1 - W) \ln(\text{familyMultiplier})$
     * - $x_3 = (1 - W) \ln(\text{patternMultiplier})$
     * - $x_4 = (1 - W) \ln(\text{formalityMultiplier})$
     * - $x_5 = W \ln(H)$
     * - $x_6 = \ln(\text{layeringMultiplier})$
     * - $x_7 = \ln(\text{footwearMultiplier})$
     * - $x_8 = (1 - W) \cdot \text{chromaMatch}$ (mean chroma gap over 0.1)
     * - $x_9 = (1 - W) \cdot \text{lightnessSpread}$ (mean lightness gap of same-hue pairs over 0.1)
     */
    fun extract(
        items: List<EngineItem>,
        itemScores: Map<Long, Double>,
        weather: EngineWeather?,
        hasFootwearInPool: Boolean,
        coWear: CoWearStats,
        itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
        subcategoryCoWear: CoWearStats = CoWearStats.EMPTY,
    ): DoubleArray {
        val meanScore = if (items.isEmpty()) 1.0 else items.map { itemScores[it.id] ?: 1.0 }.average()

        var habitWeightSum = 0.0
        var logLiftSum = 0.0
        var formalityProduct = 1.0
        var pairCount = 0
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val history = PairScorer.history(
                    items[i],
                    items[j],
                    coWear,
                    itemNeighbors,
                    subcategoryCoWear,
                )
                habitWeightSum += history.weight
                logLiftSum += ln(history.lift)
                formalityProduct *= PairScorer.formalityTerm(items[i], items[j])
                pairCount++
            }
        }

        val w = if (pairCount == 0) 0.0 else habitWeightSum / pairCount
        val logH = if (pairCount == 0) 0.0 else logLiftSum / pairCount
        val formalityMultiplier = if (pairCount == 0) 1.0 else formalityProduct.pow(1.0 / pairCount)

        val colorFactors = colorHarmonyFactors(items)
        val patternMultiplier = patternMixingMultiplier(items)
        val layeringMultiplier = layeringMultiplier(items, weather)
        val footwearMultiplier = footwearMultiplier(items, hasFootwearInPool)

        val ruleWeight = 1.0 - w

        val features = DoubleArray(TasteWeights.FEATURE_COUNT)
        features[TasteWeights.IDX_MEAN_ITEM] = ln(meanScore.coerceAtLeast(MIN_POSITIVE))
        features[TasteWeights.IDX_HUE_HARMONY] = ruleWeight * ln(colorFactors.hueMultiplier)
        features[TasteWeights.IDX_FAMILY_HARMONY] = ruleWeight * ln(colorFactors.familyMultiplier)
        features[TasteWeights.IDX_PATTERN_MIX] = ruleWeight * ln(patternMultiplier)
        features[TasteWeights.IDX_FORMALITY_GAP] = ruleWeight * ln(formalityMultiplier)
        features[TasteWeights.IDX_HISTORY_LIFT] = w * logH
        features[TasteWeights.IDX_LAYERING] = ln(layeringMultiplier)
        features[TasteWeights.IDX_FOOTWEAR] = ln(footwearMultiplier)
        features[TasteWeights.IDX_CHROMA_MATCH] = ruleWeight * ColorHarmony.chromaMatchFeature(items)
        features[TasteWeights.IDX_LIGHTNESS_SPREAD] = ruleWeight * ColorHarmony.lightnessSpreadFeature(items)
        return features
    }

    /**
     * Computes the baseline log-utility offset $\delta_{\text{base}} = \ln(\text{defaultScore}) - \beta_0^\top x$
     * between the deployed arithmetic blend $W \cdot H + (1 - W) \cdot R$ and the geometric blend
     * $H^W \cdot R^{1 - W}$ at [TasteWeights.DEFAULT] ($\beta_0 = \mathbf{1}$).
     *
     * Adding this offset to $\beta^\top x$ in [ConditionalLogitOptimizer] ensures that
     * $\text{offset} + \beta^\top x \equiv \ln(\text{defaultScore}) + (\beta - \beta_0)^\top x$,
     * matching [OutfitRecommendationEngine.scoreCombo] for all $\beta$ (including [TasteWeights.DEFAULT]).
     */
    fun baselineOffset(features: DoubleArray, defaultScore: Double): Double {
        if (defaultScore <= 0.0 || !defaultScore.isFinite()) return 0.0
        return ln(defaultScore) - TasteWeights.DEFAULT.dot(features)
    }

    /**
     * Color harmony split into [ColorHarmonyFactors.hueMultiplier] and
     * [ColorHarmonyFactors.familyMultiplier]; delegates to [ColorHarmony.factors], so their product
     * is the engine's color harmony multiplier identically.
     */
    fun colorHarmonyFactors(items: List<EngineItem>): ColorHarmonyFactors = ColorHarmony.factors(items)

    fun patternMixingMultiplier(items: List<EngineItem>): Double {
        val patternedCount = items.count { !it.isPatternSolid }
        return when {
            patternedCount == 0 -> 1.0
            patternedCount == 1 -> 1.0 + OutfitRecommendationEngine.BONUS_ONE_PATTERNED
            else -> OutfitRecommendationEngine.MULTIPLIER_MULTI_PATTERNED
        }
    }

    fun layeringMultiplier(items: List<EngineItem>, weather: EngineWeather?): Double {
        if (weather == null) return 1.0
        var multiplier = 1.0
        val hasOuter = items.any { it.warmthLayer == "Outer" }
        val tempHigh = weather.tempHighC
        val tempLow = weather.tempLowC
        if (tempHigh != null && tempHigh < OutfitRecommendationEngine.COLD_THRESHOLD_C && !hasOuter) {
            multiplier *= OutfitRecommendationEngine.MULTIPLIER_COLD_NO_OUTER
        }
        if (tempLow != null && tempLow > OutfitRecommendationEngine.WARM_THRESHOLD_C && hasOuter) {
            multiplier *= OutfitRecommendationEngine.MULTIPLIER_WARM_WITH_OUTER
        }
        return multiplier
    }

    fun footwearMultiplier(items: List<EngineItem>, hasFootwearInPool: Boolean): Double {
        return if (hasFootwearInPool && items.none { it.outfitRole == OutfitRecommendationEngine.ROLE_FOOTWEAR }) {
            OutfitRecommendationEngine.MULTIPLIER_NO_FOOTWEAR
        } else {
            1.0
        }
    }

    private const val MIN_POSITIVE = 1e-12
}
