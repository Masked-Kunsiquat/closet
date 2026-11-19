package com.closet.features.recommendations.engine.replay

import com.closet.core.data.dao.ItemLastWorn
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.core
import com.closet.features.recommendations.engine.engineInput
import com.closet.features.recommendations.engine.learning.ConditionalLogitOptimizer
import com.closet.features.recommendations.engine.learning.HistoricalStratumBuilder
import com.closet.features.recommendations.engine.learning.HistoricalWearLog
import com.closet.features.recommendations.engine.learning.TasteWeightTrainer
import com.closet.features.recommendations.engine.learning.TasteWeights

/**
 * Result of running the offline replay check across held-out wear logs.
 *
 * @property hitRateAt10        Fraction of held-out logs whose worn core appeared in the top-10 results.
 * @property heldOutLogLik      Total conditional log-likelihood across the held-out strata.
 * @property finalLearnedWeights The [TasteWeights] fitted on the history preceding the last held-out log.
 */
internal data class ReplayEvaluation(
    val hitRateAt10: Double,
    val heldOutLogLik: Double,
    val finalLearnedWeights: TasteWeights,
)

/**
 * Offline replay: for each of the last [holdOut] logs, recommend using only strictly earlier
 * history, and count a hit when the worn core is among the top-10 result cores.
 */
internal fun hitRateAt10(
    fixture: PlantedTasteFixture,
    engine: OutfitRecommendationEngine,
    holdOut: Int = 20,
    useLearnedWeights: Boolean = false,
): Double = evaluateReplay(fixture, engine, holdOut, useLearnedWeights).hitRateAt10

/**
 * Evaluates both hit-rate@10 and held-out conditional log-likelihood on the last [holdOut] logs
 * of [fixture], optionally fitting [TasteWeights] causally on `earlier` logs (`date < log.date`).
 */
internal fun evaluateReplay(
    fixture: PlantedTasteFixture,
    engine: OutfitRecommendationEngine,
    holdOut: Int = 20,
    useLearnedWeights: Boolean = false,
): ReplayEvaluation {
    require(holdOut in 1..fixture.logs.size)
    val allHistorical = fixture.logs.mapIndexed { idx, l ->
        HistoricalWearLog(logId = (idx + 1).toLong(), date = l.date, itemIds = l.itemIds)
    }
    val allStrata = HistoricalStratumBuilder.buildStrata(
        items = fixture.items,
        logs = allHistorical,
        engine = engine,
    )
    val heldLogs = fixture.logs.takeLast(holdOut)
    val heldStrata = allStrata.takeLast(holdOut)

    var hits = 0
    var totalHeldOutLL = 0.0
    var lastWeights = TasteWeights.DEFAULT

    for (i in heldLogs.indices) {
        val log = heldLogs[i]
        val earlier = fixture.logs.filter { it.date < log.date }
        val weights = if (useLearnedWeights) {
            val earlierHistorical = earlier.mapIndexed { idx, l ->
                HistoricalWearLog(logId = (idx + 1).toLong(), date = l.date, itemIds = l.itemIds)
            }
            TasteWeightTrainer.trainFromHistory(
                items = fixture.items,
                logs = earlierHistorical,
                engine = engine,
            ).weights
        } else {
            TasteWeights.DEFAULT
        }
        lastWeights = weights

        val result = engine.recommend(
            input = replayInput(fixture, earlier, log, weights),
            topN = 10,
        )
        if (result.any { it.core() == log.coreIds }) hits++

        if (i < heldStrata.size) {
            totalHeldOutLL += ConditionalLogitOptimizer.stratumLogLikelihood(heldStrata[i], weights)
        }
    }

    return ReplayEvaluation(
        hitRateAt10 = hits.toDouble() / holdOut,
        heldOutLogLik = totalHeldOutLL,
        finalLearnedWeights = lastWeights,
    )
}

private fun replayInput(
    fixture: PlantedTasteFixture,
    earlier: List<WearLog>,
    heldOut: WearLog,
    tasteWeights: TasteWeights = TasteWeights.DEFAULT,
): EngineInput {
    val lastWorn = HashMap<Long, String>()
    for (l in earlier) for (id in l.itemIds) lastWorn[id] = l.date.toString() // ascending: last wins
    return engineInput(
        candidates = fixture.items,
        lastWornDates = lastWorn.mapValues { (id, d) -> ItemLastWorn(id, d) },
        weather = null,
        today = heldOut.date,
        coWear = coWearFrom(earlier),
        tasteWeights = tasteWeights,
    )
}

/** Co-wear statistics from [logs] alone: each log counts 1, pair keys are (smaller, larger). */
private fun coWearFrom(logs: List<WearLog>): CoWearStats {
    val pairs = HashMap<Pair<Long, Long>, Double>()
    val items = HashMap<Long, Double>()
    for (l in logs) {
        val ids = l.itemIds.distinct().sorted()
        for (id in ids) items.addTo(id, 1.0)
        for (i in ids.indices) for (j in i + 1 until ids.size) pairs.addTo(ids[i] to ids[j], 1.0)
    }
    return CoWearStats(pairs, items, logs.size.toDouble())
}

private fun <K> MutableMap<K, Double>.addTo(key: K, amount: Double) {
    this[key] = (this[key] ?: 0.0) + amount
}
