package com.closet.features.recommendations.engine.replay

import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlin.test.Test
import kotlin.test.assertTrue

class ReplayCheckTest {

    private val rate by lazy { hitRateAt10(PlantedTasteFixture.build(), OutfitRecommendationEngine()) }

    @Test
    fun `hit rate does not regress below the baseline`() {
        assertTrue(rate >= BASELINE_HIT_RATE, "hit-rate@10 = $rate, baseline = $BASELINE_HIT_RATE")
    }

    @Test
    fun `hit rate improves on planted taste`() {
        assertTrue(rate > BASELINE_HIT_RATE, "hit-rate@10 = $rate, baseline = $BASELINE_HIT_RATE")
        assertTrue(rate >= IMPROVED_HIT_RATE, "hit-rate@10 = $rate, improved floor = $IMPROVED_HIT_RATE")
    }

    private companion object {
        /** Measured on the pre-v2 engine: exactly 0.05 (1 of 20 held-out logs), seed 42. */
        const val BASELINE_HIT_RATE = 0.05

        /**
         * Measured after the per-core beam (#96): exactly 0.15 (3 of 20 held-out logs), seed 42.
         * The second no-regression floor for later PRs.
         */
        const val IMPROVED_HIT_RATE = 0.15
    }
}
