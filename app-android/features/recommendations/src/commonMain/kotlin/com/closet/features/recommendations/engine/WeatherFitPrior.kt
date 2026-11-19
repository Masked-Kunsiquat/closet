package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability

/**
 * Cold-start weather-fit prior derived from an item's known attributes (`subcategoryName`,
 * `warmthLayer`, and `materials`), blended with empirical wear history via Empirical Bayes /
 * Bühlmann credibility shrinkage ($w = \frac{n}{n + K}$ with $K = 5.0$).
 *
 * @property tempLowC           Prior comfortable low temperature bound (°C).
 * @property tempHighC          Prior comfortable high temperature bound (°C).
 * @property rainSuitabilityPct Prior rain suitability rate in `[0.0, 1.0]` (`< 0.20` triggers rain penalty).
 * @property windSuitabilityPct Prior wind suitability rate in `[0.0, 1.0]` (`< 0.20` triggers wind penalty).
 */
internal data class ItemWeatherPrior(
    val tempLowC: Double,
    val tempHighC: Double,
    val rainSuitabilityPct: Double,
    val windSuitabilityPct: Double,
)

/**
 * Pure attribute-based weather-fit prior and credibility shrinkage blender (Issue #106).
 *
 * Grounded in ASHRAE Standard 55 garment insulation (*clo*) ordering and Bühlmann credibility
 * shrinkage:
 * 1. **Comfort Zone & Rate Shrinkage (Option A):** With $n$ logged weather observations and
 *    credibility weight $w = \frac{n}{n + K}$ ($K = 5$), the item's comfortable temperature band
 *    $[L_{\text{eff}}, U_{\text{eff}}]$ and rain/wind suitability rates are shrunk toward the prior
 *    baseline so 1–4 sparse logs at a single temperature cannot collapse the comfort range into a
 *    1-degree point interval.
 * 2. **Score Multiplier Blending (Option B):** The resulting suitability multiplier blends the
 *    prior decision ($m_{\text{prior}}$ at $n = 0$) with the posterior comfort-band decision
 *    ($m_{\text{post}}$) by $w = \frac{n}{n + K}$, so the prior carries the score at zero logs
 *    and empirical history smoothly takes over as logs accumulate.
 * 3. **Legacy Bare-Item Compatibility:** If an item carries no weather-informative attributes
 *    (`subcategoryName` not mapped, no mapped `materials`, and `warmthLayer` not in
 *    `{"Base", "Mid", "Outer"}`), [forItem] returns `null` and the engine falls back to the
 *    classic `logCount >= 5` sparse-data guard.
 */
internal object WeatherFitPrior {

    /** Prior pseudocount $K$ matching [OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL]. */
    const val PRIOR_WEIGHT_K = 5.0

    private const val SUITABILITY_THRESHOLD = 0.20
    private const val DEFAULT_TEMP_LOW_C = 8.0
    private const val DEFAULT_TEMP_HIGH_C = 26.0
    private const val NEUTRAL_SUITABILITY_PCT = 0.50
    private const val UNSUITABLE_PCT = 0.05
    private const val SUITABLE_PCT = 0.75

    /**
     * Base comfortable temperature bands `[lowC, highC]` by seeded subcategory name.
     * Ordered from high-insulation winter garments to low-clo summer garments.
     */
    private val SUBCATEGORY_TEMP_BANDS: Map<String, Pair<Double, Double>> = mapOf(
        // Heavy cold-weather outerwear
        "Coat" to (-10.0 to 12.0),
        // Cool / cold layers
        "Sweater" to (-5.0 to 16.0),
        "Hoodie" to (-5.0 to 18.0),
        "Sweatshirt" to (-5.0 to 18.0),
        "Cardigan" to (-2.0 to 18.0),
        "Scarf" to (-10.0 to 12.0),
        "Tights" to (-5.0 to 16.0),
        "Jacket" to (-3.0 to 18.0),
        "Raincoat" to (2.0 to 22.0),
        // Cool to mild transition garments
        "Vest" to (0.0 to 18.0),
        "Boots" to (-8.0 to 18.0),
        "Track Jacket" to (4.0 to 20.0),
        "Blazer" to (6.0 to 22.0),
        "Joggers/Sweatpants" to (0.0 to 20.0),
        // Temperate / all-around garments
        "Shirt" to (8.0 to 26.0),
        "Blouse" to (10.0 to 27.0),
        "Jeans" to (2.0 to 25.0),
        "Trousers/Slacks" to (4.0 to 26.0),
        "Chinos" to (6.0 to 26.0),
        "Leggings" to (4.0 to 24.0),
        "Dress Shoes" to (2.0 to 28.0),
        "Sneakers" to (2.0 to 30.0),
        "Jumpsuit" to (10.0 to 27.0),
        "Bodysuit" to (10.0 to 28.0),
        // Warm-weather garments
        "T-Shirt" to (14.0 to 32.0),
        "Polo" to (14.0 to 32.0),
        "Dress" to (14.0 to 32.0),
        "Skirt" to (14.0 to 32.0),
        // Hot-weather low-clo garments
        "Tank Top" to (18.0 to 38.0),
        "Shorts" to (18.0 to 38.0),
        "Athletic Shorts" to (16.0 to 38.0),
        "Sandals" to (18.0 to 38.0),
        "Romper" to (18.0 to 36.0),
        "Sports Bra" to (16.0 to 38.0),
        "One-Piece" to (20.0 to 38.0),
        "Bikini/Trunks" to (22.0 to 40.0),
        "Rash Guard" to (18.0 to 36.0),
    )

    /**
     * Fallback comfortable temperature bands by category `warmth_layer` when no subcategory
     * or material is present. `"Base"` is intentionally omitted because every basic Top/Bottom
     * starts as a base layer across all seasons (and outfit-level layering already requires an
     * `"Outer"` layer below 5 °C).
     */
    private val WARMTH_LAYER_TEMP_BANDS: Map<String, Pair<Double, Double>> = mapOf(
        "Outer" to (-10.0 to 18.0),
        "Mid" to (-2.0 to 18.0),
    )

    /**
     * Thermal shift (°C) applied to both low and high bounds for each seeded material.
     * Negative shifts move the comfort zone colder (insulating materials); positive shifts move
     * it warmer (breathable summer fabrics).
     */
    private val MATERIAL_TEMP_SHIFTS: Map<String, Double> = mapOf(
        "Cashmere" to -7.0,
        "Wool" to -6.0,
        "Fleece" to -6.0,
        "Corduroy" to -4.0,
        "Velvet" to -4.0,
        "Suede" to -3.0,
        "Leather" to -3.0,
        "Faux Leather" to -2.0,
        "Denim" to -1.0,
        "Cotton" to 0.0,
        "Polyester" to 0.0,
        "Nylon" to 0.0,
        "Spandex/Elastane" to 0.0,
        "Jersey" to 0.0,
        "Satin" to 1.0,
        "Modal" to 1.0,
        "Silk" to 2.0,
        "Rayon/Viscose" to 2.0,
        "Bamboo" to 2.0,
        "Mesh" to 5.0,
        "Linen" to 6.0,
        "Chiffon" to 6.0,
    )

    private val RAIN_UNFRIENDLY_MATERIALS: Set<String> = setOf(
        "Suede", "Silk", "Cashmere", "Velvet", "Chiffon", "Linen",
    )
    private val RAIN_READY_OUTER_MATERIALS: Set<String> = setOf(
        "Nylon", "Polyester", "Leather", "Faux Leather",
    )
    private val RAIN_UNFRIENDLY_SUBCATEGORIES: Set<String> = setOf(
        "Sandals", "Slippers",
    )
    private val RAIN_READY_SUBCATEGORIES: Set<String> = setOf(
        "Raincoat", "Boots", "Jacket", "Coat",
    )

    private val WIND_UNFRIENDLY_MATERIALS: Set<String> = setOf(
        "Chiffon", "Silk", "Linen",
    )
    private val WIND_UNFRIENDLY_SUBCATEGORIES: Set<String> = setOf(
        "Hat/Cap", "Skirt", "Sandals", "Slippers",
    )
    private val WIND_READY_SUBCATEGORIES: Set<String> = setOf(
        "Coat", "Jacket", "Raincoat", "Boots",
    )

    /**
     * Derives an [ItemWeatherPrior] from [item]'s subcategory, warmth layer, and materials,
     * or returns `null` if [item] has no weather-informative attributes.
     */
    fun forItem(item: EngineItem): ItemWeatherPrior? {
        val subBand = item.subcategoryName?.let { SUBCATEGORY_TEMP_BANDS[it] }
        val layerBand = item.warmthLayer?.let { WARMTH_LAYER_TEMP_BANDS[it] }
        val mappedMaterials = item.materials.filter { it in MATERIAL_TEMP_SHIFTS }
        val hasRainSub = item.subcategoryName in RAIN_UNFRIENDLY_SUBCATEGORIES ||
            item.subcategoryName in RAIN_READY_SUBCATEGORIES
        val hasWindSub = item.subcategoryName in WIND_UNFRIENDLY_SUBCATEGORIES ||
            item.subcategoryName in WIND_READY_SUBCATEGORIES

        if (subBand == null && layerBand == null && mappedMaterials.isEmpty() && !hasRainSub && !hasWindSub) {
            return null
        }

        val (baseLow, baseHigh) = subBand ?: layerBand ?: (DEFAULT_TEMP_LOW_C to DEFAULT_TEMP_HIGH_C)
        val materialShift = if (mappedMaterials.isEmpty()) {
            0.0
        } else {
            mappedMaterials.sumOf { MATERIAL_TEMP_SHIFTS[it] ?: 0.0 } / mappedMaterials.size
        }

        val priorLow = baseLow + materialShift
        val priorHigh = baseHigh + materialShift

        val isOuter = item.warmthLayer == "Outer" || item.outfitRole == OutfitRecommendationEngine.ROLE_OUTERWEAR
        val rainPct = when {
            item.subcategoryName == "Raincoat" -> 0.90
            item.subcategoryName in RAIN_UNFRIENDLY_SUBCATEGORIES -> UNSUITABLE_PCT
            item.materials.any { it in RAIN_UNFRIENDLY_MATERIALS } -> UNSUITABLE_PCT
            isOuter && item.materials.any { it in RAIN_READY_OUTER_MATERIALS } -> SUITABLE_PCT
            item.subcategoryName in RAIN_READY_SUBCATEGORIES -> SUITABLE_PCT
            else -> NEUTRAL_SUITABILITY_PCT
        }

        val windPct = when {
            isOuter || item.subcategoryName in WIND_READY_SUBCATEGORIES -> SUITABLE_PCT
            item.subcategoryName in WIND_UNFRIENDLY_SUBCATEGORIES -> UNSUITABLE_PCT
            !isOuter && item.materials.any { it in WIND_UNFRIENDLY_MATERIALS } -> UNSUITABLE_PCT
            else -> NEUTRAL_SUITABILITY_PCT
        }

        return ItemWeatherPrior(
            tempLowC = priorLow,
            tempHighC = priorHigh,
            rainSuitabilityPct = rainPct,
            windSuitabilityPct = windPct,
        )
    }

    /**
     * Computes the temperature suitability multiplier for an item using the combo shrinkage model:
     * 1. For sparse histories ($n < 5$), shrinks empirical temperature percentiles $[p_{10}, p_{90}]$
     *    toward $[L_0, U_0]$ with weight $w = \frac{n}{n + K}$ so small log counts don't collapse
     *    the comfort band; once $n \ge 5$, uses the raw empirical $[p_{10}, p_{90}]$ band directly.
     * 2. Blends the prior multiplier ($m_{\text{prior}}$) and empirical/posterior-band multiplier
     *    ($m_{\text{post}}$) by $w = \frac{n}{n + K}$.
     *
     * When [prior] is `null`, falls back to the legacy `logCount >= 5` rule.
     */
    fun temperatureMultiplier(
        prior: ItemWeatherPrior?,
        percentiles: ItemTempPercentiles?,
        forecastLow: Double,
        forecastHigh: Double,
    ): Double {
        val penalty = OutfitRecommendationEngine.MULTIPLIER_TEMP_OUT_OF_RANGE
        if (prior == null) {
            if (percentiles != null && percentiles.logCount >= OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) {
                val outside = forecastHigh < percentiles.p10TempLow || forecastLow > percentiles.p90TempHigh
                if (outside) return penalty
            }
            return 1.0
        }

        val priorOutside = forecastHigh < prior.tempLowC || forecastLow > prior.tempHighC
        val mPrior = if (priorOutside) penalty else 1.0

        val n = percentiles?.logCount ?: 0
        if (n <= 0 || percentiles == null) return mPrior

        val w = n.toDouble() / (n.toDouble() + PRIOR_WEIGHT_K)
        val postOutside = if (n >= OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) {
            forecastHigh < percentiles.p10TempLow || forecastLow > percentiles.p90TempHigh
        } else {
            val effLow = w * percentiles.p10TempLow + (1.0 - w) * prior.tempLowC
            val effHigh = w * percentiles.p90TempHigh + (1.0 - w) * prior.tempHighC
            forecastHigh < effLow || forecastLow > effHigh
        }
        val mPost = if (postOutside) penalty else 1.0

        return w * mPost + (1.0 - w) * mPrior
    }

    /**
     * Computes the rain suitability multiplier when `weather.isRaining == true`.
     * Uses Beta-Binomial rate shrinkage $p_{\text{eff}} = w \cdot \hat{p} + (1 - w) \cdot \pi_0$
     * plus credibility multiplier blending $w \cdot m_{\text{post}} + (1 - w) \cdot m_{\text{prior}}$.
     */
    fun rainMultiplier(
        prior: ItemWeatherPrior?,
        rain: ItemRainSuitability?,
    ): Double {
        val penalty = OutfitRecommendationEngine.MULTIPLIER_RAIN_NOT_SUITABLE
        if (prior == null) {
            if (rain != null && rain.rainLogCount >= OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) {
                if (rain.rainPct < SUITABILITY_THRESHOLD) return penalty
            }
            return 1.0
        }

        val mPrior = if (prior.rainSuitabilityPct < SUITABILITY_THRESHOLD) penalty else 1.0
        val n = rain?.rainLogCount ?: 0
        if (n <= 0 || rain == null) return mPrior

        val w = n.toDouble() / (n.toDouble() + PRIOR_WEIGHT_K)
        val effRainPct = w * rain.rainPct + (1.0 - w) * prior.rainSuitabilityPct
        val mPost = if (effRainPct < SUITABILITY_THRESHOLD) penalty else 1.0

        return w * mPost + (1.0 - w) * mPrior
    }

    /**
     * Computes the wind suitability multiplier when `weather.isWindy == true`.
     * Uses Beta-Binomial rate shrinkage $p_{\text{eff}} = w \cdot \hat{p} + (1 - w) \cdot \pi_0$
     * plus credibility multiplier blending $w \cdot m_{\text{post}} + (1 - w) \cdot m_{\text{prior}}$.
     */
    fun windMultiplier(
        prior: ItemWeatherPrior?,
        wind: ItemWindSuitability?,
    ): Double {
        val penalty = OutfitRecommendationEngine.MULTIPLIER_WIND_NOT_SUITABLE
        if (prior == null) {
            if (wind != null && wind.windLogCount >= OutfitRecommendationEngine.MIN_LOGS_FOR_SIGNAL) {
                if (wind.windPct < SUITABILITY_THRESHOLD) return penalty
            }
            return 1.0
        }

        val mPrior = if (prior.windSuitabilityPct < SUITABILITY_THRESHOLD) penalty else 1.0
        val n = wind?.windLogCount ?: 0
        if (n <= 0 || wind == null) return mPrior

        val w = n.toDouble() / (n.toDouble() + PRIOR_WEIGHT_K)
        val effWindPct = w * wind.windPct + (1.0 - w) * prior.windSuitabilityPct
        val mPost = if (effWindPct < SUITABILITY_THRESHOLD) penalty else 1.0

        return w * mPost + (1.0 - w) * mPrior
    }
}
