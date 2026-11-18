package com.closet.features.recommendations.engine.replay

import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.item
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random

/** One worn outfit: the date and the ids worn (core ids are the Top + Bottom ids). */
internal data class WearLog(val date: LocalDate, val itemIds: List<Long>, val coreIds: Set<Long>)

/** A favourite (top, bottom, shoe) triple the synthetic owner wears often. */
internal data class PlantedTriple(val topId: Long, val bottomId: Long, val shoeId: Long)

/**
 * A deterministic synthetic wardrobe plus a wear history with planted taste: ~70% of logs are
 * one of a few favourite triples, ~30% are random valid outfits. Logs are in ascending date order.
 */
internal class PlantedTasteFixture(
    val items: List<EngineItem>,
    val planted: List<PlantedTriple>,
    val logs: List<WearLog>,
) {
    companion object {
        private val COLORS = listOf(
            "Black", "White", "Navy", "Gray", "Beige", "Red", "Green", "Blue", "Brown", "Pink"
        )
        private const val TOPS = 20
        private const val BOTTOMS = 15
        private const val SHOES = 15
        private const val OUTER = 10
        private const val PLANTED = 6
        private const val LOG_COUNT = 120
        private const val SPAN_DAYS = 180

        fun build(seed: Int = 42): PlantedTasteFixture {
            val rnd = Random(seed)
            var nextId = 1L
            fun make(role: String, count: Int, warmth: String?): List<EngineItem> = List(count) {
                val colors = if (rnd.nextInt(5) == 0) {
                    setOf(COLORS[rnd.nextInt(COLORS.size)], COLORS[rnd.nextInt(COLORS.size)])
                } else {
                    setOf(COLORS[rnd.nextInt(COLORS.size)])
                }
                item(
                    id = nextId++, role = role, warmthLayer = warmth,
                    colorFamilies = colors, isPatternSolid = rnd.nextInt(4) != 0
                )
            }
            val tops = make("Top", TOPS, "Base")
            val bottoms = make("Bottom", BOTTOMS, "Base")
            val shoes = make("Footwear", SHOES, null)
            val outer = make("Outerwear", OUTER, "Outer")

            val planted = List(PLANTED) { i ->
                PlantedTriple(tops[i].id, bottoms[i].id, shoes[rnd.nextInt(SHOES)].id)
            }

            val start = LocalDate(2026, 1, 1)
            val logs = List(LOG_COUNT) { i ->
                val date = start.plus(i * SPAN_DAYS / LOG_COUNT, DateTimeUnit.DAY)
                if (rnd.nextInt(100) < 70) {
                    val t = planted[rnd.nextInt(PLANTED)]
                    WearLog(date, listOf(t.topId, t.bottomId, t.shoeId), setOf(t.topId, t.bottomId))
                } else {
                    val top = tops[rnd.nextInt(TOPS)].id
                    val bottom = bottoms[rnd.nextInt(BOTTOMS)].id
                    val ids = mutableListOf(top, bottom, shoes[rnd.nextInt(SHOES)].id)
                    if (rnd.nextInt(3) == 0) ids += outer[rnd.nextInt(OUTER)].id
                    WearLog(date, ids, setOf(top, bottom))
                }
            }
            return PlantedTasteFixture(tops + bottoms + shoes + outer, planted, logs)
        }
    }
}
