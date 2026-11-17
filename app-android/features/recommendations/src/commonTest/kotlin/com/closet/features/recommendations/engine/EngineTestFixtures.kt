package com.closet.features.recommendations.engine

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
    subcategoryId: Long? = null
) = EngineItem(
    id = id,
    name = "Item $id",
    imagePath = null,
    categoryId = null,
    subcategoryId = subcategoryId,
    outfitRole = role,
    warmthLayer = warmthLayer,
    colorFamilies = colorFamilies,
    isPatternSolid = isPatternSolid
)

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
    today: LocalDate? = null
) = EngineInput(
    candidates = candidates,
    tempPercentiles = tempPercentiles,
    rainSuitability = rainSuitability,
    windSuitability = windSuitability,
    lastWornDates = lastWornDates,
    weather = weather,
    today = today
)
