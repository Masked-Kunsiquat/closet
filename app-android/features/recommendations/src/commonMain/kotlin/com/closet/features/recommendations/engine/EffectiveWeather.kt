package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.weather.ItemWarmth
import com.closet.core.data.weather.WarmthLevel

/**
 * An item's effective [WarmthLevel] and rain-readiness (#147): the user's override when set,
 * else the attribute-derived value ([ItemWarmth]) refined by the item's own wear history.
 * Pure; the pipeline computes these before the engine runs.
 *
 * ## Typical lower comfort bound per level
 * The coldest daily low (°C) an item of each level is typically worn at. The scale follows the
 * ASHRAE garment insulation (clo) ordering that [ItemWarmth] uses: each level adds roughly one
 * layer's worth of clo, and each added layer extends comfort to colder air. The values are taken
 * from the low ends of [WeatherFitPrior]'s subcategory temperature bands, so the two models agree:
 *
 * | Level  | Bound  | Matching WeatherFitPrior band low                       |
 * |--------|--------|---------------------------------------------------------|
 * | NONE   | 14 °C  | T-Shirt, Polo (14)                                      |
 * | LIGHT  | 6 °C   | Blazer (6); between Track Jacket (4) and Shirt (8)      |
 * | MEDIUM | -3 °C  | Jacket (-3); between Cardigan (-2) and Sweater (-5)     |
 * | WARM   | -10 °C | Coat (-10)                                              |
 * | HEAVY  | none   | the top of the scale: there is no warmer level to reach |
 *
 * ## History refinement
 * With at least [OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL] logs, an item whose
 * 10th-percentile worn low is more than [WARMTH_MARGIN_C] below its level's bound is evidently
 * warmer than its type suggests (a fleece-lined jacket the user wears in deep cold), so its level
 * rises by exactly one. History never lowers a level: wearing a coat on mild days says nothing
 * about how warm it is.
 *
 * A not-rain-ready item becomes rain-ready when its rain suitability, credibility-blended with a
 * neutral prior by [WeatherFitPrior.credibilityBlend] (K = 5), reaches [RAIN_READY_BLENDED_PCT]:
 * the user keeps choosing it on rainy days. History never turns rain-readiness off.
 */
internal object EffectiveWeather {

    /** How far below a level's bound the p10 worn low must fall before the level rises. */
    const val WARMTH_MARGIN_C = 3.0

    /** Blended rain suitability at which history marks an item rain-ready. */
    const val RAIN_READY_BLENDED_PCT = 0.6

    /** Typical lower comfort bound (°C) per level; see the class KDoc. HEAVY has none. */
    private val LOWER_BOUND_C: Map<WarmthLevel, Double> = mapOf(
        WarmthLevel.NONE to 14.0,
        WarmthLevel.LIGHT to 6.0,
        WarmthLevel.MEDIUM to -3.0,
        WarmthLevel.WARM to -10.0,
    )

    /**
     * The effective level: [override] (a stored [WarmthLevel.ordinal]; out of range means no
     * override), else [derived] raised by at most one level by [temps].
     */
    fun warmth(derived: WarmthLevel, override: Int?, temps: ItemTempPercentiles?): WarmthLevel {
        override?.let { WarmthLevel.entries.getOrNull(it) }?.let { return it }
        if (temps == null || temps.logCount < OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) return derived
        val bound = LOWER_BOUND_C[derived] ?: return derived
        if (temps.p10TempLow >= bound - WARMTH_MARGIN_C) return derived
        return WarmthLevel.entries.getOrElse(derived.ordinal + 1) { derived }
    }

    /** The effective flag: [override], else [derived], else true when [rain] history shows rain-ready wear. */
    fun rainReady(derived: Boolean, override: Boolean?, rain: ItemRainSuitability?): Boolean {
        if (override != null) return override
        if (derived) return true
        if (rain == null || rain.rainLogCount < OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) return false
        val blended = WeatherFitPrior.credibilityBlend(
            n = rain.rainLogCount,
            observed = rain.rainPct,
            prior = WeatherFitPrior.NEUTRAL_SUITABILITY_PCT,
        )
        return blended >= RAIN_READY_BLENDED_PCT
    }
}

/** Seeded category name [ItemWarmth.derivedWarmth] falls back on; the only category with the Outerwear role. */
private const val CATEGORY_OUTERWEAR = "Outerwear"

/**
 * This item with [EngineItem.warmthLevel] and [EngineItem.rainReady] set to their effective values,
 * derived from its subcategory, materials and role, then refined by [EffectiveWeather]. Used by
 * every path that builds [EngineItem]s, so they agree.
 *
 * Candidate rows carry the category's `outfit_role`, not its name; in the seeded data only the
 * Outerwear category has the Outerwear role, so the role stands in for the category name.
 */
internal fun EngineItem.withEffectiveWeather(
    warmthOverride: Int?,
    rainReadyOverride: Boolean?,
    temps: ItemTempPercentiles?,
    rain: ItemRainSuitability?,
): EngineItem {
    val categoryName = if (outfitRole == OutfitRecommendationEngine.ROLE_OUTERWEAR) CATEGORY_OUTERWEAR else null
    return copy(
        warmthLevel = EffectiveWeather.warmth(
            derived = ItemWarmth.derivedWarmth(categoryName, subcategoryName, materials),
            override = warmthOverride,
            temps = temps,
        ),
        rainReady = EffectiveWeather.rainReady(
            derived = ItemWarmth.derivedRainReady(categoryName, subcategoryName, materials),
            override = rainReadyOverride,
            rain = rain,
        ),
    )
}
