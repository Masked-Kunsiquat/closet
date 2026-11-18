package com.closet.features.recommendations.engine.replay

import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import kotlin.test.Test
import kotlin.test.assertTrue

class ReplayCheckTest {

    @Test
    fun `hit rate does not regress below the baseline`() {
        val rate = hitRateAt10(PlantedTasteFixture.build(), OutfitRecommendationEngine())
        assertTrue(rate >= BASELINE_HIT_RATE, "hit-rate@10 = $rate, baseline = $BASELINE_HIT_RATE")
    }

    private companion object {
        /** Measured on the pre-v2 engine: exactly 0.05 (1 of 20 held-out logs), seed 42. */
        const val BASELINE_HIT_RATE = 0.05
    }
}
