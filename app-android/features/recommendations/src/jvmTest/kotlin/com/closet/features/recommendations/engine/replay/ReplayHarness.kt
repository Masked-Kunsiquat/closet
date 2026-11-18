package com.closet.features.recommendations.engine.replay

import com.closet.core.data.dao.ItemLastWorn
import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineInput
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.core
import com.closet.features.recommendations.engine.engineInput

/**
 * Offline replay: for each of the last [holdOut] logs, recommend using only strictly earlier
 * history, and count a hit when the worn core is among the top-10 result cores.
 */
internal fun hitRateAt10(
    fixture: PlantedTasteFixture,
    engine: OutfitRecommendationEngine,
    holdOut: Int = 20,
): Double {
    require(holdOut in 1..fixture.logs.size)
    val held = fixture.logs.takeLast(holdOut)
    var hits = 0
    for (log in held) {
        val earlier = fixture.logs.filter { it.date < log.date }
        val result = engine.recommend(
            input = replayInput(fixture, earlier, log),
            topN = 10,
            candidatesPerSlot = 3,
        )
        if (result.any { it.core() == log.coreIds }) hits++
    }
    return hits.toDouble() / holdOut
}

private fun replayInput(
    fixture: PlantedTasteFixture,
    earlier: List<WearLog>,
    heldOut: WearLog,
): EngineInput {
    val lastWorn = HashMap<Long, String>()
    for (l in earlier) for (id in l.itemIds) lastWorn[id] = l.date.toString() // ascending: last wins
    return engineInput(
        candidates = fixture.items,
        lastWornDates = lastWorn.mapValues { (id, d) -> ItemLastWorn(id, d) },
        weather = null,
        today = heldOut.date,
        coWear = coWearFrom(earlier),
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
