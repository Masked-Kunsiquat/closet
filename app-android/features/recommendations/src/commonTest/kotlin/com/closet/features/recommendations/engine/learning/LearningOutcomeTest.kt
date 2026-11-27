package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.engineInput
import com.closet.features.recommendations.engine.learning.LearningFixtures.bottoms
import com.closet.features.recommendations.engine.learning.LearningFixtures.harmoniousCores
import com.closet.features.recommendations.engine.learning.LearningFixtures.otherCores
import com.closet.features.recommendations.engine.learning.LearningFixtures.outerwear
import com.closet.features.recommendations.engine.learning.LearningFixtures.randomCoreWithShoes
import com.closet.features.recommendations.engine.learning.LearningFixtures.shoes
import com.closet.features.recommendations.engine.learning.LearningFixtures.tops
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What taste learning learns from whole wear histories, through the real entry point
 * [TasteWeightTrainer.trainFromHistory] (#135, design D5).
 */
class LearningOutcomeTest {

    private val engine = OutfitRecommendationEngine()

    private fun train(logs: List<HistoricalWearLog>): TasteTrainingResult =
        TasteWeightTrainer.trainFromHistory(items = LearningFixtures.items, logs = logs, engine = engine)

    /** Asserts the trainer accepted learned weights for [result] rather than falling back to DEFAULT. */
    private fun assertAccepted(result: TasteTrainingResult, what: String) {
        assertTrue(
            result.selectedLambda != null && result.weights != TasteWeights.DEFAULT,
            "Expected $what to be learned, got $result",
        )
    }

    @Test
    fun `shoe-less logs do not teach shoe-less outfits`() {
        // A real taste so the trainer accepts learned weights: 70% of logs wear one of three
        // favourite cores, the rest a uniformly random one. Every fifth log (20%) has no shoes;
        // the others have uniformly random shoes.
        val favourites = listOf(tops[0] to bottoms[1], tops[3] to bottoms[5], tops[6] to bottoms[2])
        val logs = LearningFixtures.logs(seed = 135) { rnd, i ->
            val (top, bottom) = if (rnd.nextDouble() < 0.7) {
                favourites.random(rnd)
            } else {
                tops.random(rnd) to bottoms.random(rnd)
            }
            val core = listOf(top.id, bottom.id)
            if (i % 5 == 0) core else core + shoes.random(rnd).id
        }
        assertEquals(24, logs.count { log -> log.itemIds.none { id -> shoes.any { it.id == id } } })

        val result = train(logs)
        assertAccepted(result, "the planted habit")
        val weights = result.weights
        assertEquals(1.0, weights.footwear, 0.0, "footwear must stay frozen at 1.0, weights = $weights")
        assertEquals(1.0, weights.layering, 0.0, "layering must stay frozen at 1.0, weights = $weights")

        val top = engine.recommend(
            input = engineInput(
                candidates = LearningFixtures.items,
                today = logs.last().date.plus(1, DateTimeUnit.DAY),
                tasteWeights = weights,
            ),
            topN = 1,
        ).single()
        assertTrue(
            top.items.any { it.outfitRole == OutfitRecommendationEngine.ROLE_FOOTWEAR },
            "Top suggestion has no footwear: ${top.items.map { it.id }}, weights = $weights",
        )
    }

    @Test
    fun `random choices invert nothing`() {
        // 120 uniformly random outfits: a random core, random shoes, and outerwear on a coin flip.
        val logs = LearningFixtures.logs(seed = 7) { rnd, _ ->
            val ids = randomCoreWithShoes(rnd)
            if (rnd.nextBoolean()) ids + outerwear.random(rnd).id else ids
        }
        // No held-out gain beats its standard error on random choices, so the trainer keeps the
        // rules as they are (and so inverts nothing).
        assertEquals(TasteWeights.DEFAULT, train(logs).weights)
    }

    @Test
    fun `random choices invert nothing whenever the gate admits a fit`() {
        // The same random-choice fixture over 20 seeds. The gate must rarely accept a fit, and
        // where it does, no weight may move more than 1 from the default (design D5, the No
        // inversion row).
        val beta0 = TasteWeights.DEFAULT.toDoubleArray()
        val accepted = ArrayList<Pair<Int, TasteWeights>>()
        for (seed in 1..20) {
            val logs = LearningFixtures.logs(seed = seed) { rnd, _ ->
                val ids = randomCoreWithShoes(rnd)
                if (rnd.nextBoolean()) ids + outerwear.random(rnd).id else ids
            }
            val result = train(logs)
            if (result.selectedLambda != null) accepted += seed to result.weights
        }
        // The two-SE gate admits a noise fit rarely (about 2% of seeds), so at most one of 20.
        assertTrue(accepted.size <= 1, "Accepted ${accepted.size} of 20 noise seeds: ${accepted.map { it.first }}")
        for ((seed, weights) in accepted) {
            val w = weights.toDoubleArray()
            for (j in w.indices) {
                assertTrue(
                    abs(w[j] - beta0[j]) <= 1.0,
                    "seed $seed weight $j = ${w[j]} is outside ${beta0[j]} plus or minus 1, weights = $weights",
                )
            }
        }
    }

    @Test
    fun `a colour matching user learns a positive hue weight`() {
        val result = train(colourMatchingLogs())
        assertAccepted(result, "the colour rule")
        assertTrue(result.weights.hueHarmony > 1.0, "Expected hueHarmony > 1.0, weights = ${result.weights}")
    }

    @Test
    fun `training is deterministic and clock free`() {
        // Accepted first, so the comparisons are between learned weights, not DEFAULT == DEFAULT.
        val first = train(colourMatchingLogs())
        assertAccepted(first, "the colour rule")
        assertTrue(first.weights.hueHarmony > 1.0, "Expected hueHarmony > 1.0, weights = ${first.weights}")
        val second = train(colourMatchingLogs())
        assertEquals(first.weights, second.weights)

        // trainFromHistory reads no `today`: every date it sees comes from the logs, so shifting
        // every log by the same offset must not change the weights.
        val shifted = train(colourMatchingLogs(shiftDays = 400))
        assertEquals(first.weights, shifted.weights)
    }

    /** 120 logs where 80% of cores are analogous or complementary pairs, each with random shoes. */
    private fun colourMatchingLogs(shiftDays: Int = 0): List<HistoricalWearLog> =
        LearningFixtures.logs(seed = 81, shiftDays = shiftDays) { rnd, _ ->
            val (top, bottom) = if (rnd.nextDouble() < 0.8) harmoniousCores.random(rnd) else otherCores.random(rnd)
            listOf(top.id, bottom.id, shoes.random(rnd).id)
        }
}
