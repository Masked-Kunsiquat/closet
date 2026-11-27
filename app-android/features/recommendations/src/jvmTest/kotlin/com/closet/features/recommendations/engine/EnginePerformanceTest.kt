package com.closet.features.recommendations.engine

import com.closet.core.data.dao.ItemLastWorn
import com.closet.features.recommendations.engine.learning.TasteWeights
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Smoke guard (not a benchmark): one large seeded recommend stays well under a second. The beam
 * extends every core with the full candidate list of each optional role (no per-slot cap).
 */
class EnginePerformanceTest {

    @Test
    fun `500 items and 300 logs recommend in under a second`() {
        assertRecommendsQuickly(wardrobeInput())
    }

    @Test
    fun `500 items and 300 logs recommend in under a second with learned taste weights`() {
        // Non-default weights take the feature-extraction path (chroma and lightness features).
        assertRecommendsQuickly(
            wardrobeInput(tasteWeights = TasteWeights(chromaMatch = 1.5, lightnessSpread = 0.5)),
        )
    }

    private fun wardrobeInput(tasteWeights: TasteWeights = TasteWeights.DEFAULT): EngineInput {
        val random = Random(7)
        val roles = listOf("Top", "Bottom", "OnePiece", "Outerwear", "Footwear", "Accessory")
        val families = listOf("Neutral", "Earth", "Cool", "Warm", "Bright")
        val items = (1L..500L).map { id ->
            val family = families.random(random)
            EngineItem(
                id = id,
                name = "Item $id",
                imagePath = null,
                categoryId = null,
                subcategoryId = random.nextLong(1, 30),
                outfitRole = roles[(id % roles.size).toInt()],
                warmthLayer = if (id % 6 == 3L) "Outer" else "Base",
                colorFamilies = setOf(family),
                isPatternSolid = random.nextInt(4) != 0,
                colorHexes = listOf(ColorHex("#%06X".format(random.nextInt(0x1000000)), family)),
                formalityLevel = if (random.nextBoolean()) random.nextInt(1, 6).toDouble() else null,
            )
        }
        val byRole = items.groupBy { it.outfitRole }
        val pairs = HashMap<Pair<Long, Long>, Double>()
        val counts = HashMap<Long, Double>()
        val lastWorn = HashMap<Long, ItemLastWorn>()
        val start = LocalDate(2026, 1, 1)
        repeat(300) { day ->
            val ids = listOf("Top", "Bottom", "Footwear", "Outerwear")
                .map { byRole.getValue(it).random(random).id }
                .sorted()
            for (id in ids) {
                counts[id] = (counts[id] ?: 0.0) + 1.0
                lastWorn[id] = ItemLastWorn(id, start.plus(day / 2, DateTimeUnit.DAY).toString())
            }
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                val key = ids[i] to ids[j]
                pairs[key] = (pairs[key] ?: 0.0) + 1.0
            }
        }
        val input = engineInput(
            candidates = items,
            lastWornDates = lastWorn,
            weather = weather(tempLow = 8.0, tempHigh = 16.0),
            today = LocalDate(2026, 6, 1),
            coWear = CoWearStats(pairs, counts, 300.0),
            tasteWeights = tasteWeights,
        )
        return input
    }

    private fun assertRecommendsQuickly(input: EngineInput) {
        val engine = OutfitRecommendationEngine()
        var (result, elapsed) = measureTimedValue {
            engine.recommend(input, topN = 25, jitter = Random(11))
        }
        assertEquals(25, result.size)
        assertEquals(25, result.map { it.core() }.toSet().size, "the page has distinct cores")
        // The old per-slot cap of 3 allowed at most 3 tops; the beam draws on the whole wardrobe.
        assertTrue(result.flatMap { c -> c.items.filter { it.outfitRole == "Top" } }.toSet().size > 3)

        if (elapsed >= 1.seconds) {
            // Confirm an over-limit sample with a fresh repeat to guard against JVM JIT warmup on cold runners
            val retry = measureTimedValue {
                engine.recommend(input, topN = 25, jitter = Random(11))
            }
            elapsed = retry.duration
        }
        assertTrue(elapsed < 1.seconds, "recommend took $elapsed")
    }
}
