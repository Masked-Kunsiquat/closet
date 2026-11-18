package com.closet.features.recommendations.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [selectTopK]: strict top-k without a random, seeded Gumbel-top-k with one. */
class BeamSelectTest {

    private data class Cand(val name: String, val score: Double, val tie: Int = 0)

    private val scoreOf = { c: Cand -> c.score }
    private val tieBreak = compareBy<Cand> { it.tie }
    private val T = OutfitRecommendationEngine.TEMPERATURE

    private val pool = listOf(
        Cand("a", 0.70), Cand("b", 1.00), Cand("c", 0.90), Cand("d", 0.95), Cand("e", 0.40),
        Cand("f", 0.85), Cand("g", 0.99), Cand("h", 0.60),
    )

    @Test
    fun `a null random gives the strict top-k by score`() {
        val result = selectTopK(pool, scoreOf, k = 3, random = null, temperature = T, tieBreak = tieBreak)
        assertEquals(listOf("b", "g", "d"), result.map { it.name })
    }

    @Test
    fun `a null random breaks ties with the tie-break then input order`() {
        val tied = listOf(Cand("z", 1.0, tie = 2), Cand("y", 1.0, tie = 1), Cand("x", 1.0, tie = 2))
        val result = selectTopK(tied, scoreOf, k = 3, random = null, temperature = T, tieBreak = tieBreak)
        assertEquals(listOf("y", "z", "x"), result.map { it.name })
    }

    @Test
    fun `the same seed gives the same selection`() {
        val first = selectTopK(pool, scoreOf, k = 4, random = Random(9), temperature = T, tieBreak = tieBreak)
        val second = selectTopK(pool, scoreOf, k = 4, random = Random(9), temperature = T, tieBreak = tieBreak)
        assertEquals(first, second)
    }

    @Test
    fun `k larger than the pool returns every candidate once`() {
        repeat(50) { seed ->
            val result = selectTopK(pool, scoreOf, k = 20, random = Random(seed), temperature = T, tieBreak = tieBreak)
            assertEquals(pool.size, result.size)
            assertEquals(pool.toSet(), result.toSet(), "no duplicates, nothing lost")
        }
    }

    @Test
    fun `seeded selection never repeats a candidate`() {
        repeat(200) { seed ->
            val result = selectTopK(pool, scoreOf, k = 5, random = Random(seed), temperature = T, tieBreak = tieBreak)
            assertEquals(5, result.size)
            assertEquals(5, result.toSet().size, "seed $seed: $result")
        }
    }

    @Test
    fun `a runner-up is sometimes picked and a weak candidate almost never`() {
        // The runner-up is within 1% of the best: the near-tie the temperature lets rotate.
        val partials = listOf(Cand("best", 1.0), Cand("runner-up", 0.99), Cand("weak", 0.5))
        val picks = (0 until 1000).map { seed ->
            selectTopK(partials, scoreOf, k = 1, random = Random(seed), temperature = T, tieBreak = tieBreak)
                .single().name
        }
        val runnerUp = picks.count { it == "runner-up" }
        val weak = picks.count { it == "weak" }
        assertTrue(runnerUp > 0, "runner-up picked $runnerUp / 1000")
        assertTrue(weak < 50, "weak (0.5x the best) picked $weak / 1000")
        assertTrue(picks.count { it == "best" } > runnerUp, "the best is still the likeliest")
    }

    @Test
    fun `non-positive scores rank last instead of crashing`() {
        val withZero = listOf(Cand("zero", 0.0), Cand("negative", -1.0), Cand("one", 1.0))
        repeat(20) { seed ->
            val result = selectTopK(withZero, scoreOf, k = 3, random = Random(seed), temperature = T, tieBreak = tieBreak)
            assertEquals(3, result.size)
            assertEquals("one", result.first().name)
        }
    }

    @Test
    fun `k of zero or an empty pool selects nothing`() {
        assertTrue(selectTopK(pool, scoreOf, k = 0, random = Random(1), temperature = T, tieBreak = tieBreak).isEmpty())
        assertTrue(selectTopK(emptyList<Cand>(), scoreOf, k = 3, random = null, temperature = T, tieBreak = tieBreak).isEmpty())
    }
}
