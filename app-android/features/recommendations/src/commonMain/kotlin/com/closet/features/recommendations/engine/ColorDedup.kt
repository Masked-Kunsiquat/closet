package com.closet.features.recommendations.engine

/**
 * The key near-duplicate trimming groups candidates by, shared by serving
 * ([OutfitRecommendationEngine]) and training ([com.closet.features.recommendations.engine.learning.HistoricalStratumBuilder])
 * so their control sets agree. Two items of one role with the same key are near-identical.
 *
 * The colour part, in order:
 * 1. [EngineItem.colorHexes] non-empty: the sorted, distinct set of each tag's colour-name hex
 *    (`fallbackHex ?: hex`, trimmed and upper-cased). The data layer sets `fallbackHex` to the named
 *    colour's hex, so this identifies colour names and ignores measured shades: Navy and Royal Blue
 *    stay apart, two Navy items with different measured hexes collapse.
 * 2. No hexes but [EngineItem.colorFamilies] non-empty: the sorted families.
 * 3. Neither: unique to the item, so it never collapses.
 *
 * The subcategory is always part of the key.
 */
internal fun EngineItem.colorDedupKey(): String {
    val color = when {
        colorHexes.isNotEmpty() ->
            "h:" + colorHexes.map { (it.fallbackHex ?: it.hex).trim().uppercase() }.distinct().sorted().joinToString(",")
        colorFamilies.isNotEmpty() -> "f:" + colorFamilies.sorted().joinToString(",")
        else -> return "no-color-$id"
    }
    return "$color::$subcategoryId"
}
