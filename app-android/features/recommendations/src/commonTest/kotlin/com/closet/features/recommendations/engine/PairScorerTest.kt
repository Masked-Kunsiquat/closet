package com.closet.features.recommendations.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairScorerTest {

    private fun stats(pair: Double, nA: Double, nB: Double, total: Double) = CoWearStats(
        pairCounts = if (pair > 0.0) mapOf((1L to 2L) to pair) else emptyMap(),
        itemCounts = mapOf(1L to nA, 2L to nB),
        total = total,
    )

    // =========================================================================
    // History
    // =========================================================================

    @Test
    fun `no evidence gives lift 1 and weight 0`() {
        assertEquals(PairHistory(1.0, 0.0), PairScorer.history(1, 2, CoWearStats.EMPTY))
        // One item worn, the other never: the chance expectation is 0, so still no evidence.
        assertEquals(PairHistory(1.0, 0.0), PairScorer.history(1, 2, stats(0.0, 5.0, 0.0, 10.0)))
    }

    @Test
    fun `frequent co-wear lifts above 1 and stays within PAIR_MAX`() {
        // c = 4, E = 6 x 6 / 12 = 3 -> (4 + 1) / (3 + 1) = 1.25, unclamped
        val moderate = PairScorer.history(1, 2, stats(4.0, 6.0, 6.0, 12.0))
        assertEquals(1.25, moderate.lift, 1e-9)
        assertEquals(4.0 / 9.0, moderate.weight, 1e-9)
        // c = 10, E = 2_5 -> 11 / 3_5 is clamped to 1_3
        val strong = PairScorer.history(1, 2, stats(10.0, 10.0, 10.0, 40.0))
        assertTrue(strong.lift > 1.0)
        assertEquals(1.3, strong.lift, 1e-9)
    }

    @Test
    fun `pair keys are unordered`() {
        val coWear = stats(4.0, 6.0, 6.0, 12.0)
        assertEquals(PairScorer.history(1, 2, coWear), PairScorer.history(2, 1, coWear))
    }

    @Test
    fun `worn often apart drops below 1 and stays within PAIR_MIN`() {
        // c = 0, E = 2 x 2 / 10 = 0_4 -> 1 / 1_4, unclamped
        val mild = PairScorer.history(1, 2, stats(0.0, 2.0, 2.0, 10.0))
        assertEquals(1.0 / 1.4, mild.lift, 1e-9)
        assertTrue(mild.weight > 0.0)
        // c = 0, E = 5 -> 1 / 6 is clamped to 0_7; n = E = 5 gives weight 0_5
        val strong = PairScorer.history(1, 2, stats(0.0, 10.0, 10.0, 20.0))
        assertTrue(strong.lift < 1.0)
        assertEquals(0.7, strong.lift, 1e-9)
        assertEquals(0.5, strong.weight, 1e-9)
    }

    @Test
    fun `weight is capped at W_MAX`() {
        val heavy = PairScorer.history(1, 2, stats(100.0, 100.0, 100.0, 200.0))
        assertEquals(0.7, heavy.weight, 1e-9)
    }

    // =========================================================================
    // Formality
    // =========================================================================

    private fun f(level: Double?) = item(id = 1).copy(formalityLevel = level)

    @Test
    fun `formality gap of 2 or more is penalised 0_85`() {
        assertEquals(0.85, PairScorer.formalityTerm(f(5.0), f(1.0)), 1e-9)
        assertEquals(0.85, PairScorer.formalityTerm(f(2.0), f(4.0)), 1e-9)
    }

    @Test
    fun `formality gap under 2 is neutral`() {
        assertEquals(1.0, PairScorer.formalityTerm(f(5.0), f(4.0)))
        assertEquals(1.0, PairScorer.formalityTerm(f(2.0), f(3.5)))
        assertEquals(1.0, PairScorer.formalityTerm(f(3.0), f(3.0)))
    }

    @Test
    fun `unknown formality is neutral`() {
        assertEquals(1.0, PairScorer.formalityTerm(f(null), f(1.0)))
        assertEquals(1.0, PairScorer.formalityTerm(f(5.0), f(null)))
        assertEquals(1.0, PairScorer.formalityTerm(f(null), f(null)))
    }

    // =========================================================================
    // Hue
    // =========================================================================

    private fun h(vararg hexes: String) = item(id = 1).copy(colorHexes = hexes.toList())

    @Test
    fun `complementary hues get 1_05`() {
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0000"), h("#00FFFF")))  // 180 degrees
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0000"), h("#00FF80")))  // about 150 degrees
    }

    @Test
    fun `analogous hues get 1_05`() {
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0000"), h("#FF4000")))  // about 15 degrees
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0010"), h("#FF1000")))  // across 0, about 8 degrees
    }

    @Test
    fun `clashing hues get 0_90`() {
        assertEquals(0.90, PairScorer.hueTerm(h("#FF0000"), h("#00FF00")))  // 120 degrees
        assertEquals(0.90, PairScorer.hueTerm(h("#FF0000"), h("#80FF00")))  // 90 degrees
    }

    @Test
    fun `other hue gaps are neutral`() {
        assertEquals(1.0, PairScorer.hueTerm(h("#FF0000"), h("#FFAA00")))  // 40 degrees
        assertEquals(1.0, PairScorer.hueTerm(h("#FF0000"), h("#00FF40")))  // 135 degrees
    }

    @Test
    fun `achromatic or missing hex gives null`() {
        assertNull(PairScorer.hueTerm(h("#FF0000"), h("#808080")))  // grey, saturation 0
        assertNull(PairScorer.hueTerm(h("#FF0000"), h("#000000")))  // black
        assertNull(PairScorer.hueTerm(h("#FFFFFF"), h("#FF0000")))  // white
        assertNull(PairScorer.hueTerm(h("#FF0000"), h("#8C7F80")))  // muted, saturation under 0_15
        assertNull(PairScorer.hueTerm(h("#FF0000"), h()))           // no hex
        assertNull(PairScorer.hueTerm(h("#FF0000"), h("not a hex")))
    }

    @Test
    fun `the first chromatic hex sets the hue`() {
        // A grey first swatch is skipped; the cyan one is complementary to red.
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0000"), h("#808080", "#00FFFF")))
        // ARGB form is accepted.
        assertEquals(1.05, PairScorer.hueTerm(h("#FFFF0000"), h("#FF00FFFF")))
    }
}
