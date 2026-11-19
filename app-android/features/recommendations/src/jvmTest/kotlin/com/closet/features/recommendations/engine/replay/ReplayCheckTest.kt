package com.closet.features.recommendations.engine.replay

import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlin.test.Test
import kotlin.test.assertTrue

class ReplayCheckTest {

    private val engine = OutfitRecommendationEngine()
    private val plantedFixture by lazy { PlantedTasteFixture.build() }
    private val fixedEval by lazy { evaluateReplay(plantedFixture, engine, useLearnedWeights = false) }
    private val learnedEval by lazy { evaluateReplay(plantedFixture, engine, useLearnedWeights = true) }

    @Test
    fun `hit rate does not regress below the baseline`() {
        assertTrue(
            fixedEval.hitRateAt10 >= BASELINE_HIT_RATE,
            "fixed hit-rate@10 = ${fixedEval.hitRateAt10}, baseline = $BASELINE_HIT_RATE"
        )
        assertTrue(
            learnedEval.hitRateAt10 >= BASELINE_HIT_RATE,
            "learned hit-rate@10 = ${learnedEval.hitRateAt10}, baseline = $BASELINE_HIT_RATE"
        )
    }

    @Test
    fun `hit rate improves on planted taste`() {
        assertTrue(
            fixedEval.hitRateAt10 >= IMPROVED_HIT_RATE,
            "fixed hit-rate@10 = ${fixedEval.hitRateAt10}, improved floor = $IMPROVED_HIT_RATE"
        )
        assertTrue(
            learnedEval.hitRateAt10 >= IMPROVED_HIT_RATE,
            "learned hit-rate@10 = ${learnedEval.hitRateAt10}, improved floor = $IMPROVED_HIT_RATE"
        )
    }

    @Test
    fun `learned taste weights beat fixed default weights on planted taste fixture`() {
        assertTrue(
            learnedEval.heldOutLogLik > fixedEval.heldOutLogLik,
            "Expected learned held-out log-likelihood (${learnedEval.heldOutLogLik}) > fixed (${fixedEval.heldOutLogLik})"
        )
        assertTrue(
            learnedEval.hitRateAt10 >= fixedEval.hitRateAt10,
            "Expected learned hit-rate@10 (${learnedEval.hitRateAt10}) >= fixed (${fixedEval.hitRateAt10})"
        )
    }

    @Test
    fun `learned taste weights do not regress on a fixture with no planted taste`() {
        val randomFixture = PlantedTasteFixture.buildNoPlantedTaste()
        val fixedRandom = evaluateReplay(randomFixture, engine, useLearnedWeights = false)
        val learnedRandom = evaluateReplay(randomFixture, engine, useLearnedWeights = true)

        assertTrue(
            learnedRandom.hitRateAt10 >= fixedRandom.hitRateAt10,
            "Expected no regression on random fixture: learned hit-rate@10 (${learnedRandom.hitRateAt10}) vs fixed (${fixedRandom.hitRateAt10})"
        )
        assertTrue(
            learnedRandom.heldOutLogLik >= fixedRandom.heldOutLogLik - 1e-6,
            "Expected no log-likelihood regression on random fixture: learned (${learnedRandom.heldOutLogLik}) vs fixed (${fixedRandom.heldOutLogLik})"
        )
    }

    private companion object {
        /** Measured on the pre-v2 engine: exactly 0.05 (1 of 20 held-out logs), seed 42. */
        const val BASELINE_HIT_RATE = 0.05

        /**
         * Measured after the per-core beam (#96): 0.15 (3 of 20 held-out logs), seed 42.
         * The second no-regression floor for later PRs.
         */
        const val IMPROVED_HIT_RATE = 0.15
    }
}
