package com.closet.features.recommendations.engine

import kotlin.math.ln
import kotlin.random.Random

/** Scores at or below zero are floored to this before `ln`, so they rank last instead of failing. */
private const val MIN_SELECT_SCORE = 1e-9

/** The smallest uniform draw used, so `−ln(−ln(u))` stays finite when the generator returns 0. */
private const val MIN_UNIFORM = 1e-12

/**
 * "Select k" for the engine's beam (engine v2, #96): up to [k] of [candidates], best first.
 *
 * - **[random] null:** strict top-k, by [scoreOf] descending, then [tieBreak], then input order.
 * - **Otherwise:** seeded Gumbel-top-k, which samples k without replacement with probability
 *   proportional to `score^(1/temperature)`. Each candidate's key is `ln(score) / temperature + g`,
 *   `g = −ln(−ln(u))` with `u` uniform from [random], one draw per candidate in input order (so a
 *   seed is reproducible); the k largest keys are kept, largest first. A smaller [temperature]
 *   approaches strict top-k.
 *
 * Never returns a candidate twice. Pure: the only randomness is [random].
 */
internal fun <T> selectTopK(
    candidates: List<T>,
    scoreOf: (T) -> Double,
    k: Int,
    random: Random?,
    temperature: Double,
    tieBreak: Comparator<in T>? = null,
): List<T> {
    if (k <= 0 || candidates.isEmpty()) return emptyList()
    val scores = DoubleArray(candidates.size) { scoreOf(candidates[it]) }
    val order: List<Int> = if (random == null) {
        val byScore = compareByDescending<Int> { scores[it] }
        candidates.indices.sortedWith(
            if (tieBreak == null) byScore else byScore.thenComparator { a, b -> tieBreak.compare(candidates[a], candidates[b]) }
        )
    } else {
        val keys = DoubleArray(candidates.size) { i ->
            val u = random.nextDouble().coerceAtLeast(MIN_UNIFORM)
            ln(scores[i].coerceAtLeast(MIN_SELECT_SCORE)) / temperature - ln(-ln(u))
        }
        candidates.indices.sortedByDescending { keys[it] }
    }
    return order.take(k).map { candidates[it] }
}
