package com.closet.features.recommendations.engine

import com.closet.core.data.DatabaseSeeder
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
    // History Generalization & Embedding Backoff (Delivery 3)
    // =========================================================================

    @Test
    fun `Level 1 neighbor generalization lifts pair with zero direct history`() {
        val item1 = item(id = 1, subcategoryId = 10)
        val item2 = item(id = 2, subcategoryId = 20)
        // Neighbors: 1 ~ 10 (sim 0.9), 2 ~ 20 (sim 0.8)
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(10L, 0.9)),
            2L to listOf(ItemNeighbor(20L, 0.8)),
        )
        // Co-wear between neighbor 10 and neighbor 20: c = 4, E = 3 -> lift = 1.25, n = 4.0
        val coWear = CoWearStats(
            pairCounts = mapOf((10L to 20L) to 4.0),
            itemCounts = mapOf(10L to 6.0, 20L to 6.0),
            total = 12.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors)
        // lift = 1.25
        assertEquals(1.25, result.lift, 1e-9)
        // sim = 0.9 * 0.8 = 0.72, n_neighbors = 0.72 * 4.0 = 2.88
        // n_eff = 0.6 * 2.88 = 1.728
        val expectedWeight = 1.728 / (1.728 + 5.0)
        assertEquals(expectedWeight, result.weight, 1e-9)
    }

    @Test
    fun `Level 1 neighbor generalization includes direct item paired with neighbor`() {
        // Item 1 itself was worn with neighbor 20 of Item 2
        val item1 = item(id = 1)
        val item2 = item(id = 2)
        val neighbors = mapOf(
            2L to listOf(ItemNeighbor(20L, 0.8)),
        )
        // Pair (1, 20) has co-wear: c = 4, E = 3 -> lift = 1.25, n = 4.0
        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 20L) to 4.0),
            itemCounts = mapOf(1L to 6.0, 20L to 6.0),
            total = 12.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors)
        assertEquals(1.25, result.lift, 1e-9)
        // sim = 1.0 * 0.8 = 0.8, n_neighbors = 0.8 * 4.0 = 3.2, n_eff = 0.6 * 3.2 = 1.92
        val expectedWeight = 1.92 / (1.92 + 5.0)
        assertEquals(expectedWeight, result.weight, 1e-9)
    }

    @Test
    fun `Level 2 subcategory fallback provides coarse baseline when items and neighbors unseen`() {
        val item1 = item(id = 1, subcategoryId = 100)
        val item2 = item(id = 2, subcategoryId = 200)
        val subCoWear = CoWearStats(
            pairCounts = mapOf((100L to 200L) to 8.0),
            itemCounts = mapOf(100L to 10.0, 200L to 8.0),
            total = 40.0,
        )
        // E_sub = 10 * 8 / 40 = 2.0. c = 8.0. lift = (8+1)/(2+1) = 3.0 -> clamped to 1.3
        // n_sub = 8.0, n_eff = min(5.0, 0.25 * 8.0) = 2.0
        val result = PairScorer.history(item1, item2, CoWearStats.EMPTY, subcategoryCoWear = subCoWear)
        assertEquals(1.3, result.lift, 1e-9)
        val expectedWeight = 2.0 / (2.0 + 5.0)
        assertEquals(expectedWeight, result.weight, 1e-9)
    }

    @Test
    fun `sufficient direct history bypasses backoff`() {
        val item1 = item(id = 1, subcategoryId = 100)
        val item2 = item(id = 2, subcategoryId = 200)
        // Direct evidence n = 6.0 >= K (5.0): c = 6, E = 3 -> lift = (6+1)/(3+1) = 1.75 -> clamped to 1.3
        val directCoWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 6.0),
            itemCounts = mapOf(1L to 10.0, 2L to 12.0),
            total = 40.0,
        )
        // Subcategory co-wear has a different lift (e.g. low lift = 0.8)
        val subCoWear = CoWearStats(
            pairCounts = mapOf((100L to 200L) to 0.0),
            itemCounts = mapOf(100L to 10.0, 200L to 10.0),
            total = 20.0,
        )
        val result = PairScorer.history(item1, item2, directCoWear, subcategoryCoWear = subCoWear)
        // Direct lift is 1.3, weight = 6 / (6 + 5) = 6/11
        assertEquals(1.3, result.lift, 1e-9)
        assertEquals(6.0 / 11.0, result.weight, 1e-9)
    }

    @Test
    fun `direct and neighbor evidence blend smoothly when direct is sparse`() {
        val item1 = item(id = 1)
        val item2 = item(id = 2)
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(10L, 1.0)),
        )
        // Direct: c = 1, E = 1 -> n0 = 1.0, lift0 = (1+1)/(1+1) = 1.0
        // Neighbor (10, 2): c = 4, E = 1 -> lift = (4+1)/(1+1) = 2.5 -> clamped 1.3, n = 4.0
        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 1.0, (2L to 10L) to 4.0),
            itemCounts = mapOf(1L to 2.0, 2L to 5.0, 10L to 2.0),
            total = 10.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors)
        // n0 = 1.0, lift0 = 1.0
        // n1 = 0.6 * (1.0 * 4.0) = 2.4, lift1 = 1.3
        // n_eff = 1.0 + 2.4 = 3.4
        // combinedLift = (1.0 * 1.0 + 2.4 * 1.3) / 3.4 = 4.12 / 3.4 = 1.2117647...
        val expectedLift = (1.0 * 1.0 + 2.4 * 1.3) / 3.4
        assertEquals(expectedLift, result.lift, 1e-9)
        val expectedWeight = 3.4 / (3.4 + 5.0)
        assertEquals(expectedWeight, result.weight, 1e-9)
    }

    @Test
    fun `neighbor filtering excludes target pair in reverse order and deduplicates canonical pairs`() {
        val item1 = item(id = 1)
        val item2 = item(id = 2)
        // Item 2 is a neighbor of Item 1, and Item 1 is a neighbor of Item 2.
        // Also both share neighbors 10 and 20 in reverse directions.
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(2L, 0.9), ItemNeighbor(10L, 0.8), ItemNeighbor(20L, 0.75)),
            2L to listOf(ItemNeighbor(1L, 0.9), ItemNeighbor(20L, 0.8), ItemNeighbor(10L, 0.75)),
        )
        // Pair (10, 20) has co-wear: c = 4, E = 3 -> lift = 1.25, n = 4.0
        // Target pair (1, 2) has direct wear: c = 2, E = 0 -> lift = 3.0 -> clamped 1.3, n = 2.0
        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 2.0, (10L to 20L) to 4.0),
            itemCounts = mapOf(10L to 6.0, 20L to 6.0),
            total = 12.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors)
        // Direct: n0 = 2.0, lift0 = 1.3
        // In neighbor cross-product:
        // (1, 2) and (2, 1) are target pair in both orders -> excluded!
        // (1, 1) and (2, 2) -> same item -> excluded!
        // (10, 20) is visited once with sim = 0.8 * 0.8 = 0.64; reverse (20, 10) is deduplicated!
        // n1 = 0.6 * (0.64 * 4.0) = 1.536, lift1 = 1.25
        val n0 = 2.0
        val lift0 = 1.3
        val n1 = 0.6 * (0.64 * 4.0)
        val lift1 = 1.25
        val nEff = n0 + n1
        val expectedLift = (n0 * lift0 + n1 * lift1) / nEff
        assertEquals(expectedLift, result.lift, 1e-9)
        assertEquals(nEff / (nEff + 5.0), result.weight, 1e-9)
        // Symmetric: history(1, 2) must strictly equal history(2, 1)
        val reverseResult = PairScorer.history(item2, item1, coWear, itemNeighbors = neighbors)
        assertEquals(result.lift, reverseResult.lift, 1e-9)
        assertEquals(result.weight, reverseResult.weight, 1e-9)
    }

    @Test
    fun `Level 2 subcategory fallback is skipped once combined direct and neighbor evidence reaches K`() {
        val item1 = item(id = 1, subcategoryId = 100)
        val item2 = item(id = 2, subcategoryId = 200)
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(10L, 1.0)),
            2L to listOf(ItemNeighbor(20L, 1.0)),
        )
        // Direct n0 = 2.0 (c = 2, E = 0 -> lift = 1.3 clamped)
        // Neighbor (10, 20) n = 5.0, E = 3.0 -> lift = (5+1)/(3+1) = 1.5, n1 = 0.6 * 5.0 = 3.0
        // Combined n0 + n1 = 5.0 >= K (5.0) -> Level 2 MUST be skipped!
        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 2.0, (10L to 20L) to 5.0),
            itemCounts = mapOf(10L to 6.0, 20L to 6.0),
            total = 12.0,
        )
        // Subcategory co-wear with extreme low lift ((0+1)/(5+1) = 0.166)
        val subCoWear = CoWearStats(
            pairCounts = mapOf((100L to 200L) to 0.0),
            itemCounts = mapOf(100L to 10.0, 200L to 10.0),
            total = 20.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors, subcategoryCoWear = subCoWear)
        // Because n0 + n1 = 5.0 >= K, subcategory is skipped!
        // lift = ((2.0 * 1.3 + 3.0 * 1.5) / 5.0).coerceIn(0.8, 1.3) = (7.1 / 5.0) -> clamped to 1.3
        assertEquals(1.3, result.lift, 1e-9)
        assertEquals(5.0 / (5.0 + 5.0), result.weight, 1e-9)
    }

    @Test
    fun `Level 2 subcategory fallback is preserved when combined direct and neighbor evidence is under K`() {
        val item1 = item(id = 1, subcategoryId = 100)
        val item2 = item(id = 2, subcategoryId = 200)
        val neighbors = mapOf(
            1L to listOf(ItemNeighbor(10L, 1.0)),
        )
        // Direct n0 = 1.0, lift0 = 1.3 (clamped)
        // Neighbor (10, 2) n = 2.0, lift = (2+1)/(1+1) = 1.5 -> n1 = 0.6 * (1.0 * 2.0) = 1.2
        val coWear = CoWearStats(
            pairCounts = mapOf((1L to 2L) to 1.0, (2L to 10L) to 2.0),
            itemCounts = mapOf(10L to 3.0, 2L to 4.0),
            total = 12.0,
        )
        // Subcategory evidence: c = 0.0, E = 5.0 -> lift = 1/6, n_sub = 5.0
        // n2 = min(5.0, 0.25 * 5.0) = 1.25
        val subCoWear = CoWearStats(
            pairCounts = mapOf((100L to 200L) to 0.0),
            itemCounts = mapOf(100L to 10.0, 200L to 10.0),
            total = 20.0,
        )
        val result = PairScorer.history(item1, item2, coWear, itemNeighbors = neighbors, subcategoryCoWear = subCoWear)
        // Since n0 + n1 = 1.0 + 1.2 = 2.2 < K (5.0), Level 2 subcategory IS preserved!
        // To verify Level 2 was preserved: if subcategory were skipped, lift would be (1*2.0 + 1.2*1.5)/2.2 = 1.727 -> clamped to 1.3.
        // With subcategory preserved, actual lift is ~1.083 (pulled down by low subcategory lift), well below 1.3!
        assertTrue(result.lift < 1.2, "lift was ${result.lift}")
        assertTrue(result.lift > 1.0, "lift was ${result.lift}")
        val expectedWeight = 3.45 / (3.45 + 5.0)
        assertEquals(expectedWeight, result.weight, 1e-9)
    }

    @Test
    fun `buildItemNeighbors finds similar items above threshold and limits to topK`() {
        // Unit vectors in 3D:
        // vec1 = [1, 0, 0]
        // vec2 = [0.9, sqrt(1 - 0.81), 0] -> dot with vec1 = 0.9 (>= 0.75, keep)
        // vec3 = [0.8, sqrt(1 - 0.64), 0] -> dot with vec1 = 0.8 (>= 0.75, keep)
        // vec4 = [0.5, sqrt(1 - 0.25), 0] -> dot with vec1 = 0.5 (< 0.75, drop)
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.9f, 0.43588989f, 0.0f)
        val v3 = floatArrayOf(0.8f, 0.6f, 0.0f)
        val v4 = floatArrayOf(0.5f, 0.8660254f, 0.0f)

        val embeddings = mapOf(1L to v1, 2L to v2, 3L to v3, 4L to v4)
        val neighbors = buildItemNeighbors(embeddings, topK = 5, minSimilarity = 0.75)

        val neighbors1 = neighbors[1L].orEmpty()
        assertEquals(2, neighbors1.size)
        assertEquals(2L, neighbors1[0].itemId)
        assertEquals(0.9, neighbors1[0].similarity, 1e-5)
        assertEquals(3L, neighbors1[1].itemId)
        assertEquals(0.8, neighbors1[1].similarity, 1e-5)

        // Item 4 has no neighbors >= 0.75 with item 1
        assertTrue(neighbors1.none { it.itemId == 4L })
        // Item is not its own neighbor
        assertTrue(neighbors1.none { it.itemId == 1L })
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

    /** A Warm item whose tagged colors are all Warm with these hexes. */
    private fun h(vararg hexes: String) =
        item(id = 1, colorFamilies = setOf("Warm")).copy(colorHexes = hexes.map { ColorHex(it, "Warm") })

    private fun tagged(vararg colors: Pair<String, String>) = item(
        id = 1,
        colorFamilies = colors.map { it.second }.toSet(),
    ).copy(colorHexes = colors.map { (hex, family) -> ColorHex(hex, family) })

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
        assertEquals(0.90, PairScorer.hueTerm(h("#FF0000"), h("#80FF00")))  // about 90 degrees
        assertEquals(0.90, PairScorer.hueTerm(h("#FF0000"), h("#A0FF00")))  // about 82 degrees
        assertEquals(0.90, PairScorer.hueTerm(h("#FF0000"), h("#5EFF00")))  // about 98 degrees
    }

    @Test
    fun `other hue gaps are neutral`() {
        assertEquals(1.0, PairScorer.hueTerm(h("#FF0000"), h("#FFAA00")))  // 40 degrees
        assertEquals(1.0, PairScorer.hueTerm(h("#FF0000"), h("#FFFF00")))  // 60 degrees
        assertEquals(1.0, PairScorer.hueTerm(h("#FF0000"), h("#00FF00")))  // 120 degrees
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
        assertNull(PairScorer.hueTerm(h("#FF0000"), h("#ZZ00FFFF"))) // malformed alpha in 8-digit hex
    }

    @Test
    fun `the first chromatic hex sets the hue`() {
        // A grey first swatch is skipped; the cyan one is complementary to red.
        assertEquals(1.05, PairScorer.hueTerm(h("#FF0000"), h("#808080", "#00FFFF")))
        // ARGB form is accepted.
        assertEquals(1.05, PairScorer.hueTerm(h("#FFFF0000"), h("#FF00FFFF")))
    }

    private val cyan = tagged("#00FFFF" to "Cool")  // 180 degrees: complementary to red, 60 from navy

    @Test
    fun `a Navy and Red item takes its hue from Red not the Neutral Navy`() {
        // Navy (#000080) is chromatic but Neutral-family; it comes first in color-id order.
        val navyRed = tagged("#000080" to "Neutral", "#FF0000" to "Warm")
        assertEquals(1.05, PairScorer.hueTerm(navyRed, cyan))  // Red vs cyan; Navy would give 1_0
        val redNavy = tagged("#FF0000" to "Warm", "#000080" to "Neutral")
        assertEquals(PairScorer.hueTerm(navyRed, cyan), PairScorer.hueTerm(redNavy, cyan))
    }

    @Test
    fun `the dominant family's hex is preferred over an earlier one`() {
        // Warm outranks Cool, so the Red hex is used even though Royal Blue comes first.
        val blueRed = tagged("#4169E1" to "Cool", "#FF0000" to "Warm")
        assertEquals(1.05, PairScorer.hueTerm(blueRed, cyan))  // Royal Blue vs cyan would be 1_0
    }

    @Test
    fun `only Neutral-family hexes give null`() {
        val navy = item(id = 1, colorFamilies = setOf("Warm")).copy(colorHexes = listOf(ColorHex("#000080", "Neutral")))
        assertNull(PairScorer.hueTerm(navy, cyan))
    }

    @Test
    fun `no seeded same-family pair scores below 1 on hue`() {
        val seeded = DatabaseSeeder.SEEDED_COLORS
            .filter { it.family != "Neutral" }
            .map { it.name to tagged(it.hex to it.family) }
        var checked = 0
        for (i in seeded.indices) for (j in i + 1 until seeded.size) {
            val (nameA, a) = seeded[i]
            val (nameB, b) = seeded[j]
            if (a.colorFamilies != b.colorFamilies) continue
            val term = PairScorer.hueTerm(a, b) ?: continue
            assertTrue(term >= 1.0, "$nameA + $nameB scores $term")
            checked++
        }
        // 23 pairs from the v1 palette, plus Teal (Cool) with the 5 other Cool colours.
        assertEquals(28, checked, "every seeded same-family chromatic pair was checked")
    }
}
