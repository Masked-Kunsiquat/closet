package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.EngineItem
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
        // A real taste so the trainer accepts learned weights: 70% of logs wear one of six
        // favourite cores, the rest a uniformly random one. Every fifth log (20%) has no shoes;
        // the others have uniformly random shoes. Six favourites, not three: grouped CV (#142)
        // drops a validation block's own outfits from training, and three favourites land in
        // nearly every block, so no fold could learn a three-favourite habit.
        //
        // The gate stops this history at the habit stage, whose mask holds footwear and layering
        // at 1.0 anyway. So the freezing check (#135: the controls match the worn outfit's
        // optional slots, so both features are constant within every stratum) runs on a fit with
        // every feature free, at the weakest regularization.
        val favourites = listOf(
            tops[0] to bottoms[1], tops[3] to bottoms[5], tops[6] to bottoms[2],
            tops[2] to bottoms[7], tops[5] to bottoms[0], tops[7] to bottoms[3],
        )
        val logs = shoeLessLogs(favourites)
        assertEquals(24, logs.count { log -> log.itemIds.none { id -> shoes.any { it.id == id } } })

        val strata = HistoricalStratumBuilder.buildStrata(items = LearningFixtures.items, logs = logs, engine = engine)
        val free = ConditionalLogitOptimizer.fit(strata = strata, lambda = TasteWeightTrainer.LAMBDA_PATH.last())
        assertTrue(free != TasteWeights.DEFAULT, "Fixture precondition: the free fit moves some weight, got $free")
        assertEquals(1.0, free.footwear, 0.0, "footwear must stay frozen at 1.0 in a free fit, weights = $free")
        assertEquals(1.0, free.layering, 0.0, "layering must stay frozen at 1.0 in a free fit, weights = $free")

        val result = train(logs)
        assertAccepted(result, "the planted habit")
        assertTrue(result.acceptedStage != TasteTrainingStage.NONE, "Expected an accepted stage, got $result")
        val weights = result.weights
        assertEquals(1.0, weights.footwear, 0.0, "footwear must stay at 1.0, weights = $weights")
        assertEquals(1.0, weights.layering, 0.0, "layering must stay at 1.0, weights = $weights")

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
    fun `a colour matching preference is not yet detectable without leakage`() {
        // 80% of cores are analogous or complementary pairs (the rest are random non-harmonious
        // ones). Before grouped CV (#142) this passed partly because repeated outfits leaked across
        // folds. Without leakage the gain is about 1.35 standard errors at 120 logs, so the rule
        // stage is not accepted and hue stays at the default. Measured on the same generator at
        // 200, 300, 400 and 500 logs, the rule stage is never accepted either (training builds at
        // most 120 strata, and at 500 logs only the habit stage passes). The colour features'
        // small range is the likely cause, tracked in #144.
        for (count in listOf(120, 500)) {
            val result = train(colourMatchingLogs(count = count))
            assertTrue(result.acceptedStage != TasteTrainingStage.FULL, "$count logs: expected no rule stage, got $result")
            assertEquals(1.0, result.weights.hueHarmony, 0.0, "$count logs: hue must stay at the default, got $result")
        }
    }

    @Test
    fun `training is deterministic and clock free`() {
        // Accepted first, so the comparisons are between learned weights, not DEFAULT == DEFAULT.
        val first = train(LearningFixtures.reWornFavouriteLogs(seed = 142))
        assertAccepted(first, "the re-worn favourites")
        // The whole result must match: weights, stage, selected lambda, and the gate's mean and SE.
        val second = train(LearningFixtures.reWornFavouriteLogs(seed = 142))
        assertEquals(first, second)

        // trainFromHistory reads no `today`: every date it sees comes from the logs, so shifting
        // every log by the same offset must not change the result.
        val original = LearningFixtures.reWornFavouriteLogs(seed = 142)
        val shifted = train(original.map { it.copy(date = it.date.plus(400, DateTimeUnit.DAY)) })
        assertEquals(first, shifted)
    }

    /** [count] logs where 80% of cores are analogous or complementary pairs, each with random shoes. */
    private fun colourMatchingLogs(count: Int = 120): List<HistoricalWearLog> =
        LearningFixtures.logs(count = count, seed = 81) { rnd, _ ->
            val (top, bottom) = if (rnd.nextDouble() < 0.8) harmoniousCores.random(rnd) else otherCores.random(rnd)
            listOf(top.id, bottom.id, shoes.random(rnd).id)
        }

    /** 120 logs: 70% one of [favourites], the rest a random core; every fifth log has no shoes. */
    private fun shoeLessLogs(favourites: List<Pair<EngineItem, EngineItem>>): List<HistoricalWearLog> =
        LearningFixtures.logs(seed = 135) { rnd, i ->
            val (top, bottom) = if (rnd.nextDouble() < 0.7) {
                favourites.random(rnd)
            } else {
                tops.random(rnd) to bottoms.random(rnd)
            }
            val core = listOf(top.id, bottom.id)
            if (i % 5 == 0) core else core + shoes.random(rnd).id
        }

    @Test
    fun `repeated random outfits invert nothing whenever the gate admits a fit`() {
        // The demo history's structure (#142): 25 random outfits, each worn about 5 times across
        // 120 logs, over 20 seeds. The re-wear habit is real and may be learned, but the rules
        // carry no preference: the rule stage is accepted on at most one seed, a fit that stops at
        // the habit stage keeps every other weight exactly at the default, and an accepted rule
        // fit moves no weight more than 1 from the default (design D3c and D6).
        val beta0 = TasteWeights.DEFAULT.toDoubleArray()
        val fullAccepted = ArrayList<Pair<Int, TasteWeights>>()
        for (seed in 1..20) {
            val result = train(LearningFixtures.repeatedOutfitLogs(seed = seed))
            val w = result.weights.toDoubleArray()
            when (result.acceptedStage) {
                TasteTrainingStage.FULL -> fullAccepted += seed to result.weights
                else -> for (j in w.indices) {
                    if (j == TasteWeights.IDX_HISTORY_LIFT) continue
                    assertEquals(beta0[j], w[j], 0.0, "seed $seed (${result.acceptedStage}) weight $j moved: ${result.weights}")
                }
            }
        }
        assertTrue(fullAccepted.size <= 1, "Rule stage accepted on ${fullAccepted.size} of 20 repeated-noise seeds: $fullAccepted")
        for ((seed, weights) in fullAccepted) {
            val w = weights.toDoubleArray()
            for (j in w.indices) {
                if (j == TasteWeights.IDX_HISTORY_LIFT) continue
                assertTrue(
                    abs(w[j] - beta0[j]) <= 1.0,
                    "seed $seed weight $j = ${w[j]} is outside ${beta0[j]} plus or minus 1, weights = $weights",
                )
            }
        }
    }

    @Test
    fun `re-worn favourites are still learned`() {
        // Six favourite outfits worn in 70% of logs: grouping drops a validation block's own
        // favourites from training, but the habit is still learned from the other favourites.
        val result = train(LearningFixtures.reWornFavouriteLogs(seed = 142))
        assertAccepted(result, "the re-worn favourites")
        assertTrue(result.acceptedStage != TasteTrainingStage.NONE, "Expected an accepted stage, got $result")
        assertTrue(result.weights.historyLift > 1.0, "Expected historyLift > 1.0, weights = ${result.weights}")
    }
}
