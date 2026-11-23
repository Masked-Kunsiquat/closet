package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.ColorHex
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.engineInput
import com.closet.features.recommendations.engine.inColors
import com.closet.features.recommendations.engine.item
import com.closet.features.recommendations.engine.weather
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasteWeightsScoringTest {

    private val engine = OutfitRecommendationEngine()

    @Test
    fun `TasteWeights serialization round-trip and fallback`() {
        val custom = TasteWeights(
            meanItem = 1.2,
            hueHarmony = -0.35,
            familyHarmony = 0.8,
            patternMix = -0.1,
            formalityGap = 1.5,
            historyLift = 2.1,
            layering = 0.9,
            footwear = 1.1,
        )
        assertFalse(custom.isDefault)
        val raw = custom.serialize()
        val parsed = TasteWeights.deserialize(raw)
        assertEquals(custom, parsed)

        assertTrue(TasteWeights.DEFAULT.isDefault)
        assertEquals(TasteWeights.DEFAULT, TasteWeights.deserialize(null))
        assertEquals(TasteWeights.DEFAULT, TasteWeights.deserialize(""))
        assertEquals(TasteWeights.DEFAULT, TasteWeights.deserialize("1,2,3"))
        assertEquals(TasteWeights.DEFAULT, TasteWeights.deserialize("1,2,3,4,5,6,7,NaN"))
        assertEquals(TasteWeights.DEFAULT, TasteWeights.deserialize("1,2,3,4,5,6,7,Infinity"))
    }

    @Test
    fun `colorHarmonyFactors product equals legacy colorHarmonyMultiplier across mixed hex and family pairs`() {
        val redHex = item(
            id = 1L,
            role = "Top",
            colorFamilies = setOf("Bright"),
            colorHexes = listOf(ColorHex("#FF0000", "Bright")),
        )
        val cyanHex = item(
            id = 2L,
            role = "Bottom",
            colorFamilies = setOf("Cool"),
            colorHexes = listOf(ColorHex("#00FFFF", "Cool")), // complementary to red (180 deg -> 1.05)
        )
        val warmNoHex = item(
            id = 3L,
            role = "Footwear",
            colorFamilies = setOf("Warm"),
        )
        val items = listOf(redHex, cyanHex, warmNoHex)
        val itemScores = items.associate { it.id to 1.0 }

        // With W = 0, exp(DEFAULT.dot(features)) must equal legacy scoreCombo exactly
        val features = OutfitFeatureExtractor.extract(
            items = items,
            itemScores = itemScores,
            weather = null,
            hasFootwearInPool = true,
            coWear = CoWearStats.EMPTY,
        )
        val logLinearScore = exp(TasteWeights.DEFAULT.dot(features))
        val legacyScore = engine.scoreCombo(
            items = items,
            itemScores = itemScores,
            weather = null,
            hasFootwearInPool = true,
            coWear = CoWearStats.EMPTY,
            tasteWeights = TasteWeights.DEFAULT,
        )
        assertTrue(
            abs(logLinearScore - legacyScore) < 1e-12,
            "Expected logLinearScore ($logLinearScore) == legacyScore ($legacyScore)",
        )
    }

    @Test
    fun `rules-only outfit score matches exp of default dot product across all features`() {
        val top = EngineItem(
            id = 1L,
            name = "Top",
            imagePath = null,
            categoryId = null,
            subcategoryId = 10L,
            outfitRole = "Top",
            warmthLayer = "Base",
            colorFamilies = setOf("Bright"),
            isPatternSolid = false,
            colorHexes = listOf(ColorHex("#FF0000", "Bright")),
            formalityLevel = 5.0,
        )
        val bottom = EngineItem(
            id = 2L,
            name = "Bottom",
            imagePath = null,
            categoryId = null,
            subcategoryId = 20L,
            outfitRole = "Bottom",
            warmthLayer = "Base",
            colorFamilies = setOf("Bright"),
            isPatternSolid = false,
            colorHexes = emptyList(),
            formalityLevel = 1.0,
        )
        val items = listOf(top, bottom)
        val itemScores = mapOf(1L to 0.9, 2L to 0.7)
        val coldWeather = weather(tempLow = -2.0, tempHigh = 2.0)

        val legacyScore = engine.scoreCombo(
            items = items,
            itemScores = itemScores,
            weather = coldWeather,
            hasFootwearInPool = true,
            coWear = CoWearStats.EMPTY,
            tasteWeights = TasteWeights.DEFAULT,
        )
        val features = OutfitFeatureExtractor.extract(
            items = items,
            itemScores = itemScores,
            weather = coldWeather,
            hasFootwearInPool = true,
            coWear = CoWearStats.EMPTY,
        )
        val expDotScore = exp(TasteWeights.DEFAULT.dot(features))
        assertTrue(
            abs(legacyScore - expDotScore) < 1e-12,
            "Expected legacyScore ($legacyScore) == expDotScore ($expDotScore)",
        )
    }

    @Test
    fun `negative patternMix weight flips ranking in favor of multi-patterned clash`() {
        val patternedTop = item(1L, role = "Top", colorFamilies = setOf("Neutral"), isPatternSolid = false, subcategoryId = 10L)
        val solidBottom = item(2L, role = "Bottom", colorFamilies = setOf("Neutral"), isPatternSolid = true, subcategoryId = 20L)
        val patternedBottom = item(3L, role = "Bottom", colorFamilies = setOf("Neutral"), isPatternSolid = false, subcategoryId = 21L)

        val candidates = listOf(patternedTop, solidBottom, patternedBottom)

        // Default weights: 1 patterned (1+2) beats 2 patterned (1+3)
        val defaultResults = engine.recommend(
            engineInput(candidates = candidates, tasteWeights = TasteWeights.DEFAULT),
            topN = 2,
        )
        assertEquals(setOf(1L, 2L), defaultResults.first().items.map { it.id }.toSet())

        // Pattern-clash lover (negative patternMix weight): 2 patterned (1+3) beats 1 patterned (1+2)
        val clashLoverWeights = TasteWeights(patternMix = -1.5)
        val clashResults = engine.recommend(
            engineInput(candidates = candidates, tasteWeights = clashLoverWeights),
            topN = 2,
        )
        assertEquals(setOf(1L, 3L), clashResults.first().items.map { it.id }.toSet())
    }

    @Test
    fun `negative familyHarmony weight flips ranking against the same-family bonus`() {
        val redTop = item(1L, role = "Top").inColors("Red")
        val burgundyBottom = item(2L, role = "Bottom").inColors("Burgundy")
        val oliveBottom = item(3L, role = "Bottom").inColors("Olive")

        val candidates = listOf(redTop, burgundyBottom, oliveBottom)

        // Default weights: Red + Burgundy (Warm + Warm, 1.10) beats Red + Olive (hue only, 1.0)
        val defaultResults = engine.recommend(
            engineInput(candidates = candidates, tasteWeights = TasteWeights.DEFAULT),
            topN = 2,
        )
        assertEquals(setOf(1L, 2L), defaultResults.first().items.map { it.id }.toSet())

        // Negative familyHarmony weight turns the same-family bonus into a penalty
        val clashWeights = TasteWeights(familyHarmony = -1.0)
        val clashResults = engine.recommend(
            engineInput(candidates = candidates, tasteWeights = clashWeights),
            topN = 2,
        )
        assertEquals(setOf(1L, 3L), clashResults.first().items.map { it.id }.toSet())
    }
}
