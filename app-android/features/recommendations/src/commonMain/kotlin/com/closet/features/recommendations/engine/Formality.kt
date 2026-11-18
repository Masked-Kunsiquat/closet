package com.closet.features.recommendations.engine

private val OCCASION_FORMALITY: Map<String, Int> = mapOf(
    "Loungewear" to 1,
    "Athletic" to 1,
    "Casual" to 2,
    "Outdoor/Hiking" to 2,
    "Vacation" to 2,
    "Date Night" to 3,
    "Work/Business" to 4,
    "Formal" to 5,
    "Special Occasion" to 5,
)

/**
 * Formality level 1 to 5 of an item: the median of its tagged occasions' levels. Names without a
 * known level (user-created occasions) are ignored; null when no tagged occasion has a level.
 */
internal fun formalityLevel(occasionNames: List<String>): Double? {
    val levels = occasionNames.mapNotNull { OCCASION_FORMALITY[it] }.sorted()
    if (levels.isEmpty()) return null
    val mid = levels.size / 2
    return if (levels.size % 2 == 1) levels[mid].toDouble() else (levels[mid - 1] + levels[mid]) / 2.0
}
