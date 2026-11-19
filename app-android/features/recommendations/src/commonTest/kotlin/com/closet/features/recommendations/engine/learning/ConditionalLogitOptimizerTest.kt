package com.closet.features.recommendations.engine.learning

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionalLogitOptimizerTest {

    @Test
    fun `CholeskySolver solves SPD system accurately and rejects non-SPD matrix`() {
        val a = arrayOf(
            doubleArrayOf(4.0, 12.0, -16.0),
            doubleArrayOf(12.0, 37.0, -43.0),
            doubleArrayOf(-16.0, -43.0, 98.0),
        )
        val b = doubleArrayOf(1.0, 2.0, 3.0)
        val x = CholeskySolver.solve(a, b)
        assertNotNull(x)

        // Verify A * x == b
        for (i in 0 until 3) {
            var lhs = 0.0
            for (j in 0 until 3) lhs += a[i][j] * x[j]
            assertTrue(abs(lhs - b[i]) < 1e-10, "Row $i mismatch: $lhs vs ${b[i]}")
        }

        // Non-SPD matrix (negative diagonal) returns null
        val nonSpd = arrayOf(
            doubleArrayOf(1.0, 2.0),
            doubleArrayOf(2.0, 1.0),
        )
        assertNull(CholeskySolver.solve(nonSpd, doubleArrayOf(1.0, 1.0)))
    }

    @Test
    fun `cold start returns DEFAULT when strata count is below 20`() {
        val strata = List(19) {
            ChoiceStratum(
                chosenFeatures = doubleArrayOf(0.0, -0.1, -0.3, 0.0, 0.0, 0.0, 0.0, 0.0),
                controlFeatures = listOf(
                    doubleArrayOf(0.0, 0.05, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0),
                ),
            )
        }
        val fitted = ConditionalLogitOptimizer.fit(strata, lambda = 1.0)
        assertEquals(TasteWeights.DEFAULT, fitted)
    }

    @Test
    fun `zero-variance and constant-within-stratum features remain frozen at prior`() {
        val rnd = Random(123)
        // Only feature 5 (historyLift) varies within each stratum; feature 6 (layering) is a
        // non-zero constant within each stratum (e.g. day-level constant), and others are 0.0.
        val strata = List(35) { d ->
            val dayConstant = if (d % 2 == 0) -0.5 else 0.0
            val chosen = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_HISTORY_LIFT] = 0.15 + rnd.nextDouble(0.0, 0.05)
                this[TasteWeights.IDX_LAYERING] = dayConstant
            }
            val controls = List(4) {
                DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                    this[TasteWeights.IDX_HISTORY_LIFT] = -0.10 + rnd.nextDouble(-0.05, 0.05)
                    this[TasteWeights.IDX_LAYERING] = dayConstant
                }
            }
            ChoiceStratum(chosen, controls)
        }

        val fitted = ConditionalLogitOptimizer.fit(strata, lambda = 1.0)
        val arr = fitted.toDoubleArray()
        for (j in 0 until TasteWeights.FEATURE_COUNT) {
            assertTrue(arr[j].isFinite(), "Weight $j should be finite, was ${arr[j]}")
            if (j != TasteWeights.IDX_HISTORY_LIFT) {
                assertEquals(1.0, arr[j], 1e-12, "Frozen feature $j should stay at 1.0")
            } else {
                assertTrue(arr[j] > 1.0, "Active positive feature should increase above 1.0, was ${arr[j]}")
            }
        }
    }

    @Test
    fun `synthetic clash lover learns negative color and pattern harmony weights`() {
        val rnd = Random(7)
        // Generate 45 strata where the worn outfit deliberately chooses clashing hue (ln(0.90) < 0),
        // clashing Bright+Bright family (ln(0.70) < 0), and multi-patterned clash (ln(0.75) < 0),
        // while controls have harmonious positive log-multipliers.
        val strata = List(45) {
            val chosen = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_HUE_HARMONY] = ln(0.90) + rnd.nextDouble(-0.01, 0.01)
                this[TasteWeights.IDX_FAMILY_HARMONY] = ln(0.70) + rnd.nextDouble(-0.01, 0.01)
                this[TasteWeights.IDX_PATTERN_MIX] = ln(0.75) + rnd.nextDouble(-0.01, 0.01)
            }
            val controls = List(5) { k ->
                DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                    this[TasteWeights.IDX_HUE_HARMONY] = if (k % 2 == 0) ln(1.05) else 0.0
                    this[TasteWeights.IDX_FAMILY_HARMONY] = if (k % 2 == 0) ln(1.10) else ln(1.05)
                    this[TasteWeights.IDX_PATTERN_MIX] = if (k % 2 == 0) ln(1.10) else 0.0
                }
            }
            ChoiceStratum(chosen, controls)
        }

        val fitted = ConditionalLogitOptimizer.fit(strata, lambda = 1.0)
        assertTrue(fitted.hueHarmony <= 0.0, "Expected hueHarmony <= 0.0, got ${fitted.hueHarmony}")
        assertTrue(fitted.familyHarmony <= 0.0, "Expected familyHarmony <= 0.0, got ${fitted.familyHarmony}")
        assertTrue(fitted.patternMix <= 0.0, "Expected patternMix <= 0.0, got ${fitted.patternMix}")

        // Log-likelihood on the dataset must strictly improve over DEFAULT
        val defaultLL = ConditionalLogitOptimizer.totalLogLikelihood(strata, TasteWeights.DEFAULT)
        val fittedLL = ConditionalLogitOptimizer.totalLogLikelihood(strata, fitted)
        assertTrue(fittedLL > defaultLL, "Expected fittedLL ($fittedLL) > defaultLL ($defaultLL)")
    }

    @Test
    fun `complete separation stays finite under L2 regularization and respects thin-data drift clamp`() {
        // 25 strata (< 30 thin-data threshold) with complete separation on feature 4 (formalityGap)
        val strata25 = List(25) { d ->
            val chosen = DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                this[TasteWeights.IDX_FORMALITY_GAP] = ln(0.85) * (1.0 + (d % 3) * 0.1)
            }
            val controls = List(4) { k ->
                DoubleArray(TasteWeights.FEATURE_COUNT).apply {
                    this[TasteWeights.IDX_FORMALITY_GAP] = -0.01 * k
                }
            }
            ChoiceStratum(chosen, controls)
        }

        val fittedThin = ConditionalLogitOptimizer.fit(strata25, lambda = 0.1)
        assertTrue(fittedThin.formalityGap.isFinite())
        // Drift from 1.0 must be clamped to <= 0.5 when N < 30
        assertEquals(0.5, fittedThin.formalityGap, 1e-9)

        // 50 strata (>= 30) with complete separation: still finite due to lambda = 0.1
        val strata50 = strata25 + strata25
        val fitted50 = ConditionalLogitOptimizer.fit(strata50, lambda = 0.1)
        for (w in fitted50.toDoubleArray()) {
            assertTrue(w.isFinite() && abs(w) < 1000.0, "Weight remained finite: $w")
        }
    }

    @Test
    fun `determinism - identical inputs yield bit-for-bit identical TasteWeights`() {
        val rnd = Random(99)
        val strata = List(32) {
            fun randomVec() = DoubleArray(TasteWeights.FEATURE_COUNT) { rnd.nextDouble(-0.3, 0.3) }
            ChoiceStratum(
                chosenFeatures = randomVec(),
                controlFeatures = List(6) { randomVec() },
            )
        }

        val first = ConditionalLogitOptimizer.fit(strata, lambda = 3.0)
        val second = ConditionalLogitOptimizer.fit(strata, lambda = 3.0)
        assertEquals(first, second)
    }
}
