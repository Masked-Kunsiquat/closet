package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.ColorWheel
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.inColors
import com.closet.features.recommendations.engine.item
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random

/**
 * A small coloured wardrobe and seeded wear-log generators for the learning-outcome tests (#135).
 * Built here, not in the replay fixture (#133 owns colouring that one).
 *
 * Tops and bottoms each carry one chromatic seeded colour; shoes and outerwear carry none, so the
 * colour features come from the core alone. Every item is solid and has no formality, and the
 * logs carry no weather, so the layering feature is 0 everywhere.
 */
internal object LearningFixtures {

    val START: LocalDate = LocalDate(2026, 3, 1)

    private val TOP_COLORS = listOf("Red", "Royal Blue", "Forest Green", "Yellow", "Purple", "Orange", "Teal", "Pink")
    private val BOTTOM_COLORS = listOf("Navy", "Olive", "Burgundy", "Sky Blue", "Mint", "Red", "Teal", "Yellow")

    val tops: List<EngineItem> = TOP_COLORS.mapIndexed { i, color ->
        item(100L + i, role = OutfitRecommendationEngine.ROLE_TOP, subcategoryId = 10L + i).inColors(color)
    }
    val bottoms: List<EngineItem> = BOTTOM_COLORS.mapIndexed { i, color ->
        item(200L + i, role = OutfitRecommendationEngine.ROLE_BOTTOM, subcategoryId = 20L + i).inColors(color)
    }
    val shoes: List<EngineItem> = List(3) { i ->
        item(300L + i, role = OutfitRecommendationEngine.ROLE_FOOTWEAR, subcategoryId = 30L + i)
    }
    val outerwear: List<EngineItem> = List(2) { i ->
        item(400L + i, role = OutfitRecommendationEngine.ROLE_OUTERWEAR, warmthLayer = "Outer", subcategoryId = 40L + i)
    }
    val items: List<EngineItem> = tops + bottoms + shoes + outerwear

    private val allCores: List<Pair<EngineItem, EngineItem>> = tops.flatMap { t -> bottoms.map { b -> t to b } }

    /**
     * Cores whose two colours are analogous (at most 60 degrees apart) or complementary (at least
     * 150) on the painter's wheel, whatever their families. A same-family analogous pair scores on
     * the family side, so this user also follows the family rule.
     */
    val harmoniousCores: List<Pair<EngineItem, EngineItem>> = allCores.filter { (t, b) ->
        val gap = ColorWheel.gap(t.harmony!!.wheelHue, b.harmony!!.wheelHue)
        gap <= 60.0 || gap >= 150.0
    }

    /** Every other core. */
    val otherCores: List<Pair<EngineItem, EngineItem>> = allCores - harmoniousCores.toSet()

    init {
        require(harmoniousCores.isNotEmpty() && otherCores.isNotEmpty()) {
            "Fixture needs both harmonious (${harmoniousCores.size}) and other (${otherCores.size}) cores"
        }
    }

    /**
     * [count] logs on consecutive days from [START] plus [shiftDays], log ids 1 to [count]. [pick]
     * returns the item ids of log `index`, drawing from the shared seeded generator.
     */
    fun logs(
        count: Int = 120,
        seed: Int,
        shiftDays: Int = 0,
        pick: (rnd: Random, index: Int) -> List<Long>,
    ): List<HistoricalWearLog> {
        val rnd = Random(seed)
        return List(count) { i ->
            HistoricalWearLog(
                logId = (i + 1).toLong(),
                date = START.plus(i + shiftDays, DateTimeUnit.DAY),
                itemIds = pick(rnd, i),
            )
        }
    }

    /** A uniformly random core plus a uniformly random pair of shoes. */
    fun randomCoreWithShoes(rnd: Random): List<Long> =
        listOf(tops.random(rnd).id, bottoms.random(rnd).id, shoes.random(rnd).id)
}
