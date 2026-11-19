package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.item
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TasteWeightTrainerTest {

    @Test
    fun `time-ordered CV selects regularizer and improves held-out log-likelihood on consistent clash preference`() {
        val rnd = Random(42)
        // 40 time-ordered strata where the user consistently picks Bright+Bright clashing family
        // and multi-patterned outfits over harmonious alternatives.
        val strata = List(40) {
            val chosen = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_FAMILY_HARMONY] = ln(0.70) + rnd.nextDouble(-0.01, 0.01)
                this[TasteWeights.IDX_PATTERN_MIX] = ln(0.75) + rnd.nextDouble(-0.01, 0.01)
            }
            val controls = List(5) { k ->
                DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                    this[TasteWeights.IDX_FAMILY_HARMONY] = if (k % 2 == 0) ln(1.10) else 0.0
                    this[TasteWeights.IDX_PATTERN_MIX] = if (k % 2 == 0) ln(1.10) else 0.0
                }
            }
            ChoiceStratum(chosen, controls)
        }

        val result = TasteWeightTrainer.trainOnStrata(strata)
        assertEquals(40, result.strataCount)
        assertNotNull(result.selectedLambda)
        assertTrue(result.meanLogLikDelta > 0.0, "Expected positive out-of-sample log-likelihood delta, got ${result.meanLogLikDelta}")
        assertTrue(result.weights.familyHarmony < 0.0, "Expected negative familyHarmony weight, got ${result.weights.familyHarmony}")
        assertTrue(result.weights.patternMix < 0.0, "Expected negative patternMix weight, got ${result.weights.patternMix}")
    }

    @Test
    fun `pure noise choices stay at or near DEFAULT via 1-SE rule and positive-delta gate`() {
        val rnd = Random(999)
        // Generate 30 strata where features are i.i.d. symmetric zero-mean noise so no weight
        // genuinely improves out-of-sample likelihood over the prior.
        val strata = List(30) {
            fun noisyVec() = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_FAMILY_HARMONY] = if (rnd.nextBoolean()) -0.1 else 0.1
            }
            ChoiceStratum(
                chosenFeatures = noisyVec(),
                controlFeatures = List(5) { noisyVec() },
            )
        }

        val result = TasteWeightTrainer.trainOnStrata(strata)
        // Either falls back to DEFAULT directly (if meanLogLikDelta <= 0) or chooses strong regularization
        val maxDrift = result.weights.toDoubleArray().maxOf { kotlin.math.abs(it - 1.0) }
        assertTrue(maxDrift <= 0.6, "Expected noisy data weights to stay close to 1.0, maxDrift = $maxDrift")
    }

    @Test
    fun `end-to-end trainFromHistory learns habit and pattern preferences across multi-day wardrobe logs`() {
        // Build a synthetic wardrobe where the user always wears multi-patterned Bright+Bright cores
        val brightTop1 = item(1L, role = "Top", colorFamilies = setOf("Bright"), isPatternSolid = false, subcategoryId = 10L)
        val brightTop2 = item(2L, role = "Top", colorFamilies = setOf("Bright"), isPatternSolid = false, subcategoryId = 11L)
        val coolTop1 = item(3L, role = "Top", colorFamilies = setOf("Cool"), isPatternSolid = true, subcategoryId = 12L)
        val coolTop2 = item(4L, role = "Top", colorFamilies = setOf("Cool"), isPatternSolid = true, subcategoryId = 13L)

        val brightBottom1 = item(5L, role = "Bottom", colorFamilies = setOf("Bright"), isPatternSolid = false, subcategoryId = 20L)
        val brightBottom2 = item(6L, role = "Bottom", colorFamilies = setOf("Bright"), isPatternSolid = false, subcategoryId = 21L)
        val coolBottom1 = item(7L, role = "Bottom", colorFamilies = setOf("Cool"), isPatternSolid = true, subcategoryId = 22L)
        val coolBottom2 = item(8L, role = "Bottom", colorFamilies = setOf("Cool"), isPatternSolid = true, subcategoryId = 23L)

        val shoe = item(9L, role = "Footwear", colorFamilies = setOf("Neutral"), isPatternSolid = true, subcategoryId = 30L)
        val items = listOf(
            brightTop1, brightTop2, coolTop1, coolTop2,
            brightBottom1, brightBottom2, coolBottom1, coolBottom2,
            shoe,
        )

        val start = LocalDate(2026, 1, 1)
        val clashCores = listOf(1L to 5L, 1L to 6L, 2L to 5L, 2L to 6L)
        val logs = List(36) { i ->
            val (topId, bottomId) = clashCores[i % clashCores.size]
            HistoricalWearLog(
                logId = (i + 1).toLong(),
                date = start.plus(i, DateTimeUnit.DAY),
                itemIds = listOf(topId, bottomId, 9L),
            )
        }

        val result = TasteWeightTrainer.trainFromHistory(items = items, logs = logs)
        assertEquals(36, result.strataCount)
        assertTrue(result.meanLogLikDelta > 0.0, "Expected positive CV delta, got ${result.meanLogLikDelta}")
        // Both familyHarmony and patternMix penalized the user's chosen Bright+Bright patterned outfits under DEFAULT,
        // so the trainer must reduce those weights below 1.0!
        assertTrue(
            result.weights.familyHarmony < 1.0 && result.weights.patternMix < 1.0,
            "Expected familyHarmony (${result.weights.familyHarmony}) and patternMix (${result.weights.patternMix}) < 1.0",
        )
    }
}
