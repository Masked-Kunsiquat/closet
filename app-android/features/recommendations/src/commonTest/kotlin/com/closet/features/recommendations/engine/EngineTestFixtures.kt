package com.closet.features.recommendations.engine

import com.closet.core.data.DatabaseSeeder
import com.closet.core.data.dao.ItemLastWorn
import com.closet.core.data.dao.ItemRainSuitability
import com.closet.core.data.dao.ItemTempPercentiles
import com.closet.core.data.dao.ItemWindSuitability
import kotlinx.datetime.LocalDate

// Shared data builders for the engine tests (OutfitRecommendationEngineTest, EngineRankingTest).

internal fun item(
    id: Long,
    role: String? = null,
    warmthLayer: String? = null,
    colorFamilies: Set<String> = emptySet(),
    isPatternSolid: Boolean = true,
    subcategoryId: Long? = null,
    colorHexes: List<ColorHex> = emptyList(),
    formalityLevel: Double? = null,
    subcategoryName: String? = null,
    materials: Set<String> = emptySet(),
) = EngineItem(
    id = id,
    name = "Item $id",
    imagePath = null,
    categoryId = null,
    subcategoryId = subcategoryId,
    outfitRole = role,
    warmthLayer = warmthLayer,
    colorFamilies = colorFamilies,
    isPatternSolid = isPatternSolid,
    colorHexes = colorHexes,
    formalityLevel = formalityLevel,
    subcategoryName = subcategoryName,
    materials = materials,
)

/**
 * This item tagged with the seeded colours [names] (`DatabaseSeeder.SEEDED_COLORS`), each at its
 * seeded hex, family and neutrality with share 1 / n, and [EngineItem.colorFamilies] to match.
 * Color harmony reads only the tags, so a test of a colour effect needs them.
 */
internal fun EngineItem.inColors(vararg names: String): EngineItem {
    val tags = names.map { name ->
        val seed = DatabaseSeeder.SEEDED_COLORS.single { it.name == name }
        ColorHex(seed.hex, seed.family, share = 1.0 / names.size, wearsAsNeutral = seed.wearsAsNeutral)
    }
    return copy(colorFamilies = tags.map { it.family }.toSet(), colorHexes = tags)
}

internal fun weather(
    tempLow: Double? = null,
    tempHigh: Double? = null,
    isRaining: Boolean = false,
    isWindy: Boolean = false
) = EngineWeather(
    tempLowC = tempLow,
    tempHighC = tempHigh,
    isRaining = isRaining,
    isWindy = isWindy
)

internal fun engineInput(
    candidates: List<EngineItem> = emptyList(),
    tempPercentiles: Map<Long, ItemTempPercentiles> = emptyMap(),
    rainSuitability: Map<Long, ItemRainSuitability> = emptyMap(),
    windSuitability: Map<Long, ItemWindSuitability> = emptyMap(),
    lastWornDates: Map<Long, ItemLastWorn> = emptyMap(),
    weather: EngineWeather? = null,
    today: LocalDate? = null,
    coWear: CoWearStats = CoWearStats.EMPTY,
    itemNeighbors: Map<Long, List<ItemNeighbor>> = emptyMap(),
    subcategoryCoWear: CoWearStats = CoWearStats.EMPTY,
    tasteWeights: com.closet.features.recommendations.engine.learning.TasteWeights =
        com.closet.features.recommendations.engine.learning.TasteWeights.DEFAULT,
) = EngineInput(
    candidates = candidates,
    tempPercentiles = tempPercentiles,
    rainSuitability = rainSuitability,
    windSuitability = windSuitability,
    lastWornDates = lastWornDates,
    weather = weather,
    today = today,
    coWear = coWear,
    itemNeighbors = itemNeighbors,
    subcategoryCoWear = subcategoryCoWear,
    tasteWeights = tasteWeights,
)
