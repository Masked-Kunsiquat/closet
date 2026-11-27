package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.inColors
import com.closet.features.recommendations.engine.item
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            ChoiceStratum(chosen, controls, choiceKey = setOf(it.toLong()))
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
                choiceKey = setOf(it.toLong()),
            )
        }

        val result = TasteWeightTrainer.trainOnStrata(strata)
        // Either falls back to DEFAULT directly (if meanLogLikDelta <= 0) or chooses strong regularization
        val maxDrift = result.weights.toDoubleArray().maxOf { kotlin.math.abs(it - 1.0) }
        assertTrue(maxDrift <= 0.6, "Expected noisy data weights to stay close to 1.0, maxDrift = $maxDrift")
    }

    @Test
    fun `a held-out gain below two standard errors falls back to DEFAULT and a clear gain is kept`() {
        // Seed 20 of this noise generator: its chosen lambda gains a positive mean held-out
        // log-likelihood of about 1.4 standard errors (0.027 against 0.020). A one-SE gate would
        // accept it; the two-SE gate rejects it.
        val rnd = Random(20)
        val noise = List(40) {
            fun noisyVec() = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_FAMILY_HARMONY] = rnd.nextDouble(-0.2, 0.2)
                this[TasteWeights.IDX_PATTERN_MIX] = rnd.nextDouble(-0.2, 0.2)
            }
            ChoiceStratum(noisyVec(), List(5) { noisyVec() }, choiceKey = setOf(it.toLong()))
        }
        val noiseResult = TasteWeightTrainer.trainOnStrata(noise)
        val noiseSe = noiseResult.meanLogLikDeltaStandardError
        assertTrue(
            noiseResult.meanLogLikDelta > 0.0 &&
                noiseResult.meanLogLikDelta > noiseSe &&
                noiseResult.meanLogLikDelta < 2.0 * noiseSe,
            "Fixture precondition: SE < mean (${noiseResult.meanLogLikDelta}) < 2 SE (SE = $noiseSe)",
        )
        assertEquals(TasteWeights.DEFAULT, noiseResult.weights)
        assertNull(noiseResult.selectedLambda)
        assertEquals(TasteTrainingStage.NONE, noiseResult.acceptedStage)

        // The consistent clash preference: its gain is well over two standard errors, so it is kept.
        val prefRnd = Random(42)
        val preference = List(40) {
            val chosen = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_FAMILY_HARMONY] = ln(0.70) + prefRnd.nextDouble(-0.01, 0.01)
                this[TasteWeights.IDX_PATTERN_MIX] = ln(0.75) + prefRnd.nextDouble(-0.01, 0.01)
            }
            val controls = List(5) { k ->
                DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                    this[TasteWeights.IDX_FAMILY_HARMONY] = if (k % 2 == 0) ln(1.10) else 0.0
                    this[TasteWeights.IDX_PATTERN_MIX] = if (k % 2 == 0) ln(1.10) else 0.0
                }
            }
            ChoiceStratum(chosen, controls, choiceKey = setOf(it.toLong()))
        }
        val prefResult = TasteWeightTrainer.trainOnStrata(preference)
        assertTrue(
            prefResult.meanLogLikDelta >
                2.0 * prefResult.meanLogLikDeltaStandardError,
            "Expected mean (${prefResult.meanLogLikDelta}) > 2 SE (SE = ${prefResult.meanLogLikDeltaStandardError})",
        )
        assertNotNull(prefResult.selectedLambda)
        // No habit signal here (historyLift is 0 everywhere), so the rules win against DEFAULT.
        assertEquals(TasteTrainingStage.FULL, prefResult.acceptedStage)
        assertTrue(prefResult.weights != TasteWeights.DEFAULT, "Expected learned weights, got DEFAULT")
    }

    @Test
    fun `end-to-end trainFromHistory learns habit and pattern preferences across multi-day wardrobe logs`() {
        // The user always wears multi-patterned Warm + Cool cores over solid Cool + Cool ones (same
        // family, 1.10): 5 patterned warm tops by 4 patterned cool bottoms give 20 clash outfits,
        // enough distinct choices to learn from (#142, D4), each worn three times in rotation.
        val warmTops = listOf("Red", "Burgundy", "Orange", "Pink", "Yellow").mapIndexed { i, color ->
            item(1L + i, role = "Top", isPatternSolid = false, subcategoryId = 10L + i).inColors(color)
        }
        val coolTops = listOf("Sky Blue", "Teal").mapIndexed { i, color ->
            item(11L + i, role = "Top", isPatternSolid = true, subcategoryId = 20L + i).inColors(color)
        }
        val patternedBottoms = listOf("Royal Blue", "Navy", "Forest Green", "Olive").mapIndexed { i, color ->
            item(21L + i, role = "Bottom", isPatternSolid = false, subcategoryId = 30L + i).inColors(color)
        }
        val coolBottoms = listOf("Mint", "Purple").mapIndexed { i, color ->
            item(31L + i, role = "Bottom", isPatternSolid = true, subcategoryId = 40L + i).inColors(color)
        }
        val shoe = item(41L, role = "Footwear", colorFamilies = setOf("Neutral"), isPatternSolid = true, subcategoryId = 50L)
        val items = warmTops + coolTops + patternedBottoms + coolBottoms + shoe

        val start = LocalDate(2026, 1, 1)
        val clashCores = warmTops.flatMap { t -> patternedBottoms.map { b -> t.id to b.id } }
        assertEquals(TasteWeightTrainer.MIN_DISTINCT_CHOICES, clashCores.size)
        val logs = List(60) { i ->
            val (topId, bottomId) = clashCores[i % clashCores.size]
            HistoricalWearLog(
                logId = (i + 1).toLong(),
                date = start.plus(i, DateTimeUnit.DAY),
                itemIds = listOf(topId, bottomId, shoe.id),
            )
        }

        val result = TasteWeightTrainer.trainFromHistory(items = items, logs = logs)
        assertEquals(60, result.strataCount)
        assertEquals(TasteTrainingStage.FULL, result.acceptedStage, "Expected the rules to be learned, got $result")
        assertTrue(result.meanLogLikDelta > 0.0, "Expected positive CV delta, got ${result.meanLogLikDelta}")
        // Under DEFAULT, familyHarmony favoured the same-family alternatives and patternMix penalized the user's
        // chosen multi-patterned outfits, so the trainer must reduce those weights below 1.0.
        assertTrue(
            result.weights.familyHarmony < 1.0 && result.weights.patternMix < 1.0,
            "Expected familyHarmony (${result.weights.familyHarmony}) and patternMix (${result.weights.patternMix}) < 1.0",
        )

        // The original four clash outfits, worn 36 times, are below the distinct-outfit minimum:
        // DEFAULT, with no cross-validation run.
        val fourCores = listOf(1L to 21L, 1L to 22L, 2L to 21L, 2L to 22L)
        val fewResult = TasteWeightTrainer.trainFromHistory(
            items = items,
            logs = List(36) { i ->
                val (topId, bottomId) = fourCores[i % fourCores.size]
                HistoricalWearLog((i + 1).toLong(), start.plus(i, DateTimeUnit.DAY), listOf(topId, bottomId, shoe.id))
            },
        )
        assertEquals(36, fewResult.strataCount)
        assertEquals(TasteWeights.DEFAULT, fewResult.weights)
        assertNull(fewResult.selectedLambda)
        assertEquals(TasteTrainingStage.NONE, fewResult.acceptedStage)
        assertEquals(0.0, fewResult.meanLogLikDeltaStandardError, "the minimum returns before cross-validation")
    }

    // -------------------------------------------------------------------------
    // Grouped cross-validation (#142)
    // -------------------------------------------------------------------------

    /** The consistent clash preference of the first test, as one stratum with [key] as its worn core. */
    private fun clashStratum(rnd: Random, key: Set<Long>): ChoiceStratum {
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
        return ChoiceStratum(chosen, controls, choiceKey = key)
    }

    private fun assertDefault(result: TasteTrainingResult) {
        assertEquals(TasteWeights.DEFAULT, result.weights, "Expected DEFAULT, got $result")
        assertNull(result.selectedLambda, "Expected no lambda, got $result")
        assertEquals(TasteTrainingStage.NONE, result.acceptedStage)
    }

    @Test
    fun `no validation choice appears in its training fold`() {
        val rnd = Random(142)
        val keys = List(100) { setOf(rnd.nextLong(30)) }
        val strata = keys.map { clashStratum(rnd, it) }
        val folds = TasteWeightTrainer.groupedFolds(strata)
        assertEquals(5, folds.size)
        assertEquals(50, folds.first().validation.first)
        assertEquals(99, folds.last().validation.last)
        for ((f, fold) in folds.withIndex()) {
            val validationKeys = fold.validation.mapTo(HashSet()) { keys[it] }
            val expectedTrain = (0 until fold.validation.first).filter { keys[it] !in validationKeys }
            assertEquals(expectedTrain, fold.trainIndices, "fold $f training indices")
            assertTrue(fold.trainIndices.none { keys[it] in validationKeys }, "fold $f trains on a validation choice")
            if (f > 0) assertEquals(folds[f - 1].validation.last + 1, fold.validation.first, "fold $f is not contiguous")
        }
    }

    @Test
    fun `a fold trimmed below ten strata is skipped`() {
        // 40 strata: the first 20 train fold one, which validates on 20 to 23. Strata 0 to 10 share
        // the first validation block's 4 keys, so fold one trains on only 9 strata.
        val rnd = Random(5)
        val keys = List(40) { i ->
            when {
                i <= 10 -> setOf(1000L + i % 4)
                i in 20..23 -> setOf(1000L + (i - 20))
                else -> setOf(i.toLong())
            }
        }
        val folds = TasteWeightTrainer.groupedFolds(keys.map { clashStratum(rnd, it) })
        assertEquals(20, folds.first().validation.first)
        assertEquals(9, folds.first().trainIndices.size)
        assertTrue(folds.first().isSkipped)
        assertTrue(folds.drop(1).none { it.isSkipped }, "only fold one is trimmed below the minimum")
    }

    @Test
    fun `every fold trimmed below the minimum falls back to default`() {
        // 120 strata and 21 distinct choices: 9 one-off outfits, then 12 outfits rotated. Every
        // validation block of 12 holds all 12 rotated outfits, so every fold trains on 9 strata.
        val rnd = Random(77)
        val strata = List(120) { i ->
            clashStratum(rnd, if (i < 9) setOf(500L + i) else setOf(((i - 9) % 12).toLong()))
        }
        val folds = TasteWeightTrainer.groupedFolds(strata)
        assertTrue(folds.isNotEmpty() && folds.all { it.isSkipped }, "Fixture precondition: every fold skipped")
        assertDefault(TasteWeightTrainer.trainOnStrata(strata))
    }

    @Test
    fun `a single validation choice cannot pass on zero standard error`() {
        // 20 one-off outfits, then one outfit worn 20 times: every scored validation stratum is
        // the same choice, so there is one observation and no standard error to beat.
        val rnd = Random(3)
        val strata = List(40) { i -> clashStratum(rnd, if (i < 20) setOf(i.toLong()) else setOf(999L)) }
        val folds = TasteWeightTrainer.groupedFolds(strata)
        assertTrue(
            folds.filterNot { it.isSkipped }.flatMap { it.validation }.map { strata[it].choiceKey }.toSet() == setOf(setOf(999L)),
            "Fixture precondition: only one choice is scored",
        )
        assertDefault(TasteWeightTrainer.trainOnStrata(strata))
    }

    /** The naive per-stratum standard error, treating every stratum as independent. */
    private fun naiveStandardError(deltas: DoubleArray): Double {
        val mean = deltas.average()
        val sumSq = deltas.sumOf { (it - mean) * (it - mean) }
        return kotlin.math.sqrt(sumSq / (deltas.size - 1)) / kotlin.math.sqrt(deltas.size.toDouble())
    }

    @Test
    fun `duplicate validation choices do not shrink the standard error`() {
        // With no repeats the cluster-robust SE is the usual sample SE.
        val once = doubleArrayOf(0.1, 0.3, 0.2, 0.5)
        val onceKeys = listOf(setOf(1L), setOf(2L), setOf(3L), setOf(4L))
        val (onceMean, onceSe) = TasteWeightTrainer.clusterMeanAndStandardError(once, onceKeys)
        assertEquals(once.average(), onceMean, 1e-12)
        assertEquals(naiveStandardError(once), onceSe, 1e-12)

        // Choice 2 worn four times, with the same delta each time. The mean stays weighted by
        // wears, but the repeats are one cluster: the SE does not shrink the way four
        // independent strata would make it.
        val repeated = doubleArrayOf(0.1, 0.3, 0.3, 0.2, 0.3, 0.5, 0.3)
        val repeatedKeys = listOf(setOf(1L), setOf(2L), setOf(2L), setOf(3L), setOf(2L), setOf(4L), setOf(2L))
        val (repMean, repSe) = TasteWeightTrainer.clusterMeanAndStandardError(repeated, repeatedKeys)
        assertEquals(repeated.average(), repMean, 1e-12)
        val naiveRepSe = naiveStandardError(repeated)
        assertTrue(naiveRepSe < onceSe, "Fixture precondition: naive SE shrinks with repeats ($naiveRepSe vs $onceSe)")
        assertTrue(repSe > naiveRepSe, "Cluster SE $repSe must exceed the naive SE $naiveRepSe")

        // Worked example: deltas 1, 1, 3 with the first two one choice. Mean 5/3, cluster
        // residual sums -4/3 and 4/3, so SE = sqrt(2 * 32/9) / 3 = 8/9.
        val (m, se) = TasteWeightTrainer.clusterMeanAndStandardError(
            doubleArrayOf(1.0, 1.0, 3.0),
            listOf(setOf(1L), setOf(1L), setOf(2L)),
        )
        assertEquals(5.0 / 3.0, m, 1e-12)
        assertEquals(8.0 / 9.0, se, 1e-12)
    }

    @Test
    fun `fewer than twenty distinct choices fall back to default`() {
        assertEquals(20, TasteWeightTrainer.MIN_DISTINCT_CHOICES)
        val rnd = Random(19)
        val strata = List(80) { i -> clashStratum(rnd, setOf((i % 19).toLong())) }
        val result = TasteWeightTrainer.trainOnStrata(strata)
        assertDefault(result)
        assertEquals(80, result.strataCount)
    }

    @Test
    fun `twenty distinct choices are enough to learn`() {
        val rnd = Random(20)
        val strata = List(80) { i -> clashStratum(rnd, setOf((i % 20).toLong())) }
        val result = TasteWeightTrainer.trainOnStrata(strata)
        assertNotNull(result.selectedLambda, "Expected the clash preference to be learned, got $result")
        assertEquals(TasteTrainingStage.FULL, result.acceptedStage)
        assertTrue(result.weights.familyHarmony < 1.0, "weights = ${result.weights}")
    }
}
