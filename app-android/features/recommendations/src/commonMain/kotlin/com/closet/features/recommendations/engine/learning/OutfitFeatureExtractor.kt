package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.EngineWeather
import com.closet.features.recommendations.engine.ItemNeighbor
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.PairScorer
import com.closet.features.recommendations.engine.dominantFamilyOf
import kotlin.math.ln
import kotlin.math.pow

/**
 * Decomposed color harmony multipliers for an outfit:
 * - [hueMultiplier]: geometric mean of hue-rule contributions across non-Neutral item pairs.
 * - [familyMultiplier]: geometric mean of coarse family-rule contributions across non-Neutral item pairs.
 *
 * By construction, `hueMultiplier * familyMultiplier` equals the legacy combined
 * `colorHarmonyMultiplier` identically.
 */
internal data class ColorHarmonyFactors(
    val hueMultiplier: Double,
    val familyMultiplier: Double,
) {
    val combined: Double get() = hueMultiplier * familyMultiplier
}

/**
 * Pure feature extractor that maps a candidate (or partial) outfit into the 8-dimensional
 * log-feature vector $x \in \mathbb{R}^8$ consumed by [TasteWeights] and the conditional logit solver.
 */
internal object OutfitFeatureExtractor {

    /**
     * Extracts the 8-dimensional feature vector $x$ for [items]:
     * - $x_0 = \ln(\text{meanItem})$
     * - $x_1 = (1 - W) \ln(\text{hueMultiplier})$
     * - $x_2 = (1 - W) \ln(\text{familyMultiplier})$
     * - $x_3 = (1 - W) \ln(\text{patternMultiplier})$
     * - $x_4 = (1 - W) \ln(\text{formalityMultiplier})$
     * - $x_5 = W \ln(H)$
     * - $x_6 = \ln(\text{layeringMultiplier})$
     * - $x_7 = \ln(\text{footwearMultiplier})$
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
        return features
    }

    /**
     * Decomposes color harmony across non-Neutral item pairs into [ColorHarmonyFactors.hueMultiplier]
     * and [ColorHarmonyFactors.familyMultiplier] such that their product equals the legacy
     * `colorHarmonyMultiplier` identically.
     */
    fun colorHarmonyFactors(items: List<EngineItem>): ColorHarmonyFactors {
        val nonNeutral = items
            .map { it to dominantFamilyOf(it.colorFamilies) }
            .filter { (_, family) -> family != OutfitRecommendationEngine.FAMILY_NEUTRAL }
        if (nonNeutral.size < 2) return ColorHarmonyFactors(1.0, 1.0)

        var hueProduct = 1.0
        var familyProduct = 1.0
        var pairCount = 0
        for (i in nonNeutral.indices) {
            for (j in i + 1 until nonNeutral.size) {
                val (a, familyA) = nonNeutral[i]
                val (b, familyB) = nonNeutral[j]
                val hue = PairScorer.hueTerm(a, b)
                val familyRule = pairHarmonyMultiplier(familyA, familyB)
                val usesHue = hue != null && (familyA != familyB || hue >= familyRule)
                if (usesHue) {
                    hueProduct *= hue
                } else {
                    familyProduct *= familyRule
                }
                pairCount++
            }
        }
        val invCount = 1.0 / pairCount
        return ColorHarmonyFactors(
            hueMultiplier = hueProduct.pow(invCount),
            familyMultiplier = familyProduct.pow(invCount),
        )
    }

    fun pairHarmonyMultiplier(a: String, b: String): Double {
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
