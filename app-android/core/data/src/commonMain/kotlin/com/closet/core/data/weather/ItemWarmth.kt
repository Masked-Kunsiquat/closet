package com.closet.core.data.weather

/** How much insulation an item adds, from none (a tee, shoes) to heavy (a wool coat). */
enum class WarmthLevel { NONE, LIGHT, MEDIUM, WARM, HEAVY }

/**
 * Pure derivation of an item's [WarmthLevel] and rain-readiness from its seeded category,
 * subcategory and material names (see `DatabaseSeeder`). The item form shows these as the default
 * and the recommendation pipeline uses them when the item has no stored override.
 *
 * ## Grounding
 * Levels follow the ordering of garment insulation (clo) in the ASHRAE Handbook Fundamentals
 * garment insulation table, as reproduced on Wikipedia "Clothing insulation": T-shirt 0.08,
 * long-sleeve shirt 0.25, thick sweater 0.36, lined suit jacket about 0.44, shoes 0.02,
 * boots 0.10. A single garment adds little on its own, so the scale is about what an item
 * contributes as a layer, not an absolute clo figure.
 *
 * ## Warmth table (by subcategory)
 * - [WarmthLevel.NONE]: T-Shirt, Tank Top, Blouse, Shirt, Polo, Bodysuit, every Bottoms,
 *   Dresses & Jumpsuits, Footwear, Activewear (except Track Jacket), Underwear & Intimates,
 *   Swimwear and Bags subcategory, and Accessories not listed below.
 * - [WarmthLevel.LIGHT]: Sweatshirt, Hoodie, Cardigan, Blazer, Vest, Raincoat, Track Jacket,
 *   Scarf, Hat/Cap.
 * - [WarmthLevel.MEDIUM]: Sweater, Jacket.
 * - [WarmthLevel.WARM]: Coat.
 *
 * A subcategory not in the table falls back on its category: Outerwear is LIGHT, every other
 * category (or none) is NONE.
 *
 * ## Material modifiers
 * Wool, Cashmere or Fleece raise the level by one, once, and only for an item that starts at
 * LIGHT or above (a wool tee stays NONE). Linen or Mesh lower it by one, once. When both kinds
 * are present they cancel. The result is clamped to NONE..HEAVY.
 *
 * ## Rain-readiness
 * Raincoat always. Otherwise any material in [RAIN_UNFRIENDLY_MATERIALS] vetoes it (suede boots
 * are not rain-ready), matching `WeatherFitPrior`'s precedence. Then Boots; Jacket and Coat only
 * when a material is in [RAIN_PROTECTIVE_MATERIALS]; everything else false.
 */
object ItemWarmth {

    // Seeded category names.
    private const val CATEGORY_OUTERWEAR = "Outerwear"

    // Seeded subcategory names.
    private const val SUB_RAINCOAT = "Raincoat"
    private const val SUB_BOOTS = "Boots"
    private const val SUB_JACKET = "Jacket"
    private const val SUB_COAT = "Coat"

    // Seeded material names.
    private const val MAT_NYLON = "Nylon"
    private const val MAT_POLYESTER = "Polyester"
    private const val MAT_LEATHER = "Leather"
    private const val MAT_FAUX_LEATHER = "Faux Leather"
    private const val MAT_WOOL = "Wool"
    private const val MAT_CASHMERE = "Cashmere"
    private const val MAT_FLEECE = "Fleece"
    private const val MAT_LINEN = "Linen"
    private const val MAT_MESH = "Mesh"

    /**
     * Rain-protective subcategories, moved from `WeatherFitPrior` with the same contents. Raincoat
     * and Boots protect on their own; Jacket and Coat only when also made of a protective material.
     */
    val RAIN_PROTECTIVE_SUBCATEGORIES: Set<String> =
        setOf(SUB_RAINCOAT, SUB_BOOTS, SUB_JACKET, SUB_COAT)

    /** Rain-protective materials (for outer layers), moved from `WeatherFitPrior` with the same contents. */
    val RAIN_PROTECTIVE_MATERIALS: Set<String> =
        setOf(MAT_NYLON, MAT_POLYESTER, MAT_LEATHER, MAT_FAUX_LEATHER)

    /**
     * Materials that rain damages or soaks through, moved from `WeatherFitPrior` with the same
     * contents. Any of them vetoes derived rain-readiness, except on a Raincoat.
     */
    val RAIN_UNFRIENDLY_MATERIALS: Set<String> = setOf(
        "Suede", "Silk", "Cashmere", "Velvet", "Chiffon", MAT_LINEN,
    )

    /** Subcategories that protect from rain regardless of material. */
    private val ALWAYS_RAIN_READY: Set<String> = setOf(SUB_RAINCOAT, SUB_BOOTS)

    private val SUBCATEGORY_WARMTH: Map<String, WarmthLevel> = mapOf(
        "Sweatshirt" to WarmthLevel.LIGHT,
        "Hoodie" to WarmthLevel.LIGHT,
        "Cardigan" to WarmthLevel.LIGHT,
        "Blazer" to WarmthLevel.LIGHT,
        "Vest" to WarmthLevel.LIGHT,
        SUB_RAINCOAT to WarmthLevel.LIGHT,
        "Track Jacket" to WarmthLevel.LIGHT,
        "Scarf" to WarmthLevel.LIGHT,
        "Hat/Cap" to WarmthLevel.LIGHT,
        "Sweater" to WarmthLevel.MEDIUM,
        SUB_JACKET to WarmthLevel.MEDIUM,
        SUB_COAT to WarmthLevel.WARM,
    )

    private val WARMING_MATERIALS: Set<String> = setOf(MAT_WOOL, MAT_CASHMERE, MAT_FLEECE)
    private val COOLING_MATERIALS: Set<String> = setOf(MAT_LINEN, MAT_MESH)

    fun derivedWarmth(categoryName: String?, subcategoryName: String?, materials: Set<String>): WarmthLevel {
        val base = subcategoryName?.let { SUBCATEGORY_WARMTH[it] }
            ?: if (categoryName == CATEGORY_OUTERWEAR) WarmthLevel.LIGHT else WarmthLevel.NONE
        var steps = 0
        if (base >= WarmthLevel.LIGHT && materials.any { it in WARMING_MATERIALS }) steps++
        if (materials.any { it in COOLING_MATERIALS }) steps--
        val levels = WarmthLevel.entries
        return levels[(base.ordinal + steps).coerceIn(0, levels.lastIndex)]
    }

    fun derivedRainReady(categoryName: String?, subcategoryName: String?, materials: Set<String>): Boolean {
        if (subcategoryName == null || subcategoryName !in RAIN_PROTECTIVE_SUBCATEGORIES) return false
        if (subcategoryName == SUB_RAINCOAT) return true
        if (materials.any { it in RAIN_UNFRIENDLY_MATERIALS }) return false
        if (subcategoryName in ALWAYS_RAIN_READY) return true
        return materials.any { it in RAIN_PROTECTIVE_MATERIALS }
    }
}
