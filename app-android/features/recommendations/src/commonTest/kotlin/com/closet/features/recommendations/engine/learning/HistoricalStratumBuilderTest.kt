package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.CoWearStats
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import com.closet.features.recommendations.engine.inColors
import com.closet.features.recommendations.engine.item
import com.closet.features.recommendations.engine.weather
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoricalStratumBuilderTest {

    private val top1 = item(1L, role = "Top", colorFamilies = setOf("Cool"), subcategoryId = 10L)
    private val top2 = item(2L, role = "Top", colorFamilies = setOf("Warm"), subcategoryId = 11L)
    private val bottom1 = item(3L, role = "Bottom", colorFamilies = setOf("Neutral"), subcategoryId = 20L)
    private val bottom2 = item(4L, role = "Bottom", colorFamilies = setOf("Earth"), subcategoryId = 21L)
    private val shoe1 = item(5L, role = "Footwear", colorFamilies = setOf("Neutral"), subcategoryId = 30L)
    private val wardrobe = listOf(top1, top2, bottom1, bottom2, shoe1)

    @Test
    fun `zero future leakage - adding future logs on d+1 does not change stratum d features`() {
        val baseLogs = listOf(
            HistoricalWearLog(1L, LocalDate(2026, 3, 1), listOf(1L, 3L, 5L)),
            HistoricalWearLog(2L, LocalDate(2026, 3, 2), listOf(2L, 4L, 5L)),
            HistoricalWearLog(3L, LocalDate(2026, 3, 5), listOf(1L, 3L, 5L)),
        )
        val extendedLogs = baseLogs + listOf(
            HistoricalWearLog(4L, LocalDate(2026, 3, 6), listOf(1L, 3L, 5L)),
            HistoricalWearLog(5L, LocalDate(2026, 3, 10), listOf(2L, 3L, 5L)),
        )

        val baseStrata = HistoricalStratumBuilder.buildStrata(wardrobe, baseLogs)
        val extendedStrata = HistoricalStratumBuilder.buildStrata(wardrobe, extendedLogs)

        assertEquals(3, baseStrata.size)
        assertEquals(5, extendedStrata.size)

        for (i in baseStrata.indices) {
            assertContentEquals(
                baseStrata[i].chosenFeatures,
                extendedStrata[i].chosenFeatures,
                "Stratum $i chosen features changed when future logs were added",
            )
            assertEquals(baseStrata[i].controlFeatures.size, extendedStrata[i].controlFeatures.size)
            for (k in baseStrata[i].controlFeatures.indices) {
                assertContentEquals(
                    baseStrata[i].controlFeatures[k],
                    extendedStrata[i].controlFeatures[k],
                    "Stratum $i control $k changed when future logs were added",
                )
            }
        }
    }

    @Test
    fun `core deduplication IIA - adding extra accessories does not inflate competing core count`() {
        val acc1 = item(6L, role = "Accessory", colorFamilies = setOf("Neutral"), subcategoryId = 40L)
        val acc2 = item(7L, role = "Accessory", colorFamilies = setOf("Earth"), subcategoryId = 41L)
        val acc3 = item(8L, role = "Accessory", colorFamilies = setOf("Cool"), subcategoryId = 42L)

        val logs = listOf(
            HistoricalWearLog(1L, LocalDate(2026, 4, 1), listOf(1L, 3L, 5L)),
        )

        // 2 Tops x 2 Bottoms = 4 total cores. One is worn (1+3), so exactly 3 competing cores exist.
        val strataNoAcc = HistoricalStratumBuilder.buildStrata(wardrobe, logs)
        val strataWithAcc = HistoricalStratumBuilder.buildStrata(wardrobe + listOf(acc1, acc2, acc3), logs)

        assertEquals(1, strataNoAcc.size)
        assertEquals(1, strataWithAcc.size)
        assertEquals(3, strataNoAcc.first().controlFeatures.size)
        assertEquals(3, strataWithAcc.first().controlFeatures.size)
    }

    @Test
    fun `same-day multi-log excludes other same-day worn core from controls and avoids same-day co-wear leakage`() {
        val sameDay = LocalDate(2026, 5, 1)
        val logs = listOf(
            HistoricalWearLog(10L, sameDay, listOf(1L, 3L, 5L)), // core {1, 3}
            HistoricalWearLog(11L, sameDay, listOf(2L, 4L, 5L)), // core {2, 4}
        )

        val strata = HistoricalStratumBuilder.buildStrata(wardrobe, logs)
        assertEquals(2, strata.size)

        // 4 total cores ({1,3}, {1,4}, {2,3}, {2,4}). Since both {1,3} and {2,4} were worn on sameDay,
        // only 2 competing controls ({1,4} and {2,3}) remain for each stratum!
        assertEquals(2, strata[0].controlFeatures.size)
        assertEquals(2, strata[1].controlFeatures.size)

        // Neither stratum should have non-zero historyLift (feature 5), because priorLogs (date < sameDay) is empty
        assertEquals(0.0, strata[0].chosenFeatures[TasteWeights.IDX_HISTORY_LIFT], 1e-12)
        assertEquals(0.0, strata[1].chosenFeatures[TasteWeights.IDX_HISTORY_LIFT], 1e-12)
    }

    @Test
    fun `item availability uses min of createdDate and firstWornDate for bulk-imported wardrobes`() {
        // Suppose top2 has a createdDate in June 2026 (from bulk import), but was worn on April 1, 2026.
        // Suppose top3 was created in June 2026 and never worn in April.
        val top3 = item(9L, role = "Top", colorFamilies = setOf("Bright"), subcategoryId = 12L)
        val createdDates = mapOf(
            1L to LocalDate(2026, 1, 1),
            2L to LocalDate(2026, 6, 1), // postdates April 1 log, but worn on April 1
            3L to LocalDate(2026, 1, 1),
            4L to LocalDate(2026, 1, 1),
            5L to LocalDate(2026, 1, 1),
            9L to LocalDate(2026, 6, 1), // created in June, never worn in April -> excluded on April 1 & 2
        )
        val logs = listOf(
            HistoricalWearLog(1L, LocalDate(2026, 4, 1), listOf(2L, 3L, 5L)),
            HistoricalWearLog(2L, LocalDate(2026, 4, 2), listOf(1L, 3L, 5L)),
        )

        val strata = HistoricalStratumBuilder.buildStrata(
            items = wardrobe + top3,
            logs = logs,
            createdDates = createdDates,
        )
        assertEquals(2, strata.size)
        // On April 1 & 2, top1 and top2 are available (2 tops x 2 bottoms = 4 cores -> 3 controls),
        // while top3 is not yet created.
        assertTrue(strata.all { it.controlFeatures.size == 3 })
    }

    @Test
    fun `partial snapshot with missing item id is excluded from forming a stratum and its core is excluded from same-day controls`() {
        val sameDay = LocalDate(2026, 4, 1)
        // Log 1 references item 999L (archived/deleted, not in wardrobe) alongside valid core {2, 4}.
        // Log 2 is a complete snapshot on the same day with core {1, 3}.
        val logs = listOf(
            HistoricalWearLog(1L, sameDay, listOf(2L, 4L, 999L)),
            HistoricalWearLog(2L, sameDay, listOf(1L, 3L, 5L)),
        )

        val strata = HistoricalStratumBuilder.buildStrata(wardrobe, logs)
        // Only Log 2 forms a positive stratum
        assertEquals(1, strata.size)
        assertEquals(2L, strata.first().logId)
        // 4 total cores ({1,3}, {1,4}, {2,3}, {2,4}). Both {1,3} and {2,4} were worn on sameDay,
        // so Log 2's stratum must have only 2 competing controls ({1,4} and {2,3}).
        assertEquals(2, strata.first().controlFeatures.size)
    }

    // -------------------------------------------------------------------------
    // Uniform, seeded, slot-matched controls (#135)
    // -------------------------------------------------------------------------

    private val palette = listOf(
        "Black", "White", "Navy", "Red", "Royal Blue", "Forest Green", "Olive", "Burgundy",
        "Pink", "Orange", "Yellow", "Purple", "Brown", "Tan", "Teal", "Mint",
    )

    /**
     * A wardrobe in seeded colours with varied patterns and formality. Ids by role: tops from 100,
     * bottoms from 200, one-pieces from 300, shoes from 400, outerwear from 500 (all `Outer`),
     * accessories from 600.
     */
    private fun richWardrobe(
        tops: Int,
        bottoms: Int,
        onePieces: Int = 0,
        shoes: Int = 0,
        outerwear: Int = 0,
        accessories: Int = 0,
    ): List<EngineItem> {
        fun role(base: Long, count: Int, role: String, warmth: String? = null) = (0 until count).map { i ->
            item(
                id = base + i,
                role = role,
                warmthLayer = warmth,
                isPatternSolid = i % 3 != 0,
                subcategoryId = base + i % 4,
                formalityLevel = (i % 5) / 4.0,
            ).inColors(palette[(i * 5 + base.toInt() / 100) % palette.size])
        }
        return role(100, tops, "Top") + role(200, bottoms, "Bottom") +
            role(300, onePieces, "OnePiece") + role(400, shoes, "Footwear") +
            role(500, outerwear, "Outerwear", warmth = "Outer") + role(600, accessories, "Accessory")
    }

    private fun mixedLogs(): List<HistoricalWearLog> {
        val start = LocalDate(2026, 6, 1)
        return (0 until 24).map { d ->
            val ids = mutableListOf(100L + d % 6, 200L + (d * 5) % 6)
            if (d % 4 != 0) ids += 400L + d % 3 // a quarter of the logs have no shoes
            if (d % 4 == 1 || d % 6 == 0) ids += 500L + d % 2
            if (d % 5 == 1) ids += 600L + d % 3
            if (d % 7 == 2) ids += 601L + d % 2
            val w = when (d % 3) {
                0 -> weather(tempLow = 0.0, tempHigh = 6.0)
                1 -> weather(tempLow = 24.0, tempHigh = 31.0)
                else -> null
            }
            HistoricalWearLog(1000L + d, start.plus(DatePeriod(days = d)), ids.distinct(), w)
        }
    }

    @Test
    fun `footwear and layering are constant within every stratum`() {
        val items = richWardrobe(tops = 6, bottoms = 6, shoes = 3, outerwear = 2, accessories = 3)
        val strata = HistoricalStratumBuilder.buildStrata(items, mixedLogs())

        assertEquals(24, strata.size)
        for (s in strata) {
            assertTrue(s.controlFeatures.isNotEmpty())
            for (c in s.controlFeatures) {
                assertEquals(
                    s.chosenFeatures[TasteWeights.IDX_FOOTWEAR],
                    c[TasteWeights.IDX_FOOTWEAR],
                    "log ${s.logId} footwear",
                )
                assertEquals(
                    s.chosenFeatures[TasteWeights.IDX_LAYERING],
                    c[TasteWeights.IDX_LAYERING],
                    "log ${s.logId} layering",
                )
            }
        }
        // The fixture really exercises more than one footwear and layering value.
        assertTrue(strata.map { it.chosenFeatures[TasteWeights.IDX_FOOTWEAR] }.distinct().size > 1)
        assertTrue(strata.map { it.chosenFeatures[TasteWeights.IDX_LAYERING] }.distinct().size > 1)
    }

    @Test
    fun `controls are not rule ranked`() {
        // Cores only, and one log per build, so every item score is 1.0 and a control's default
        // score is exp of its offset plus its default-weighted features.
        val items = richWardrobe(tops = 12, bottoms = 12)
        val tops = items.filter { it.outfitRole == "Top" }
        val bottoms = items.filter { it.outfitRole == "Bottom" }
        val engine = OutfitRecommendationEngine()
        fun score(core: List<EngineItem>) = engine.scoreCombo(
            items = core,
            itemScores = emptyMap(),
            weather = null,
            hasFootwearInPool = false,
            coWear = CoWearStats.EMPTY,
        )

        var controlSum = 0.0
        var controlCount = 0
        var allSum = 0.0
        var allCount = 0
        for (n in 0 until 40) {
            val worn = listOf(tops[n % 12], bottoms[(n * 7) % 12])
            val log = HistoricalWearLog(5000L + n, LocalDate(2026, 7, 1), worn.map { it.id })
            val stratum = HistoricalStratumBuilder.buildStrata(items, listOf(log), engine = engine).single()
            assertEquals(HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM, stratum.controlFeatures.size)
            stratum.controlFeatures.forEachIndexed { k, f ->
                controlSum += exp(stratum.controlBaselineOffsets[k] + TasteWeights.DEFAULT.dot(f))
                controlCount++
            }
            for (t in tops) for (b in bottoms) {
                if (t == worn[0] && b == worn[1]) continue
                allSum += score(listOf(t, b))
                allCount++
            }
        }
        val controlMean = controlSum / controlCount
        val allMean = allSum / allCount
        assertTrue(
            abs(controlMean - allMean) < 0.02,
            "control mean $controlMean vs all-core mean $allMean",
        )
    }

    private fun assertSameStrata(expected: List<ChoiceStratum>, actual: List<ChoiceStratum>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            assertEquals(e.logId, a.logId)
            assertContentEquals(e.chosenFeatures, a.chosenFeatures)
            assertEquals(e.chosenBaselineOffset, a.chosenBaselineOffset)
            assertEquals(e.controlFeatures.size, a.controlFeatures.size)
            for (k in e.controlFeatures.indices) {
                assertContentEquals(e.controlFeatures[k], a.controlFeatures[k], "log ${e.logId} control $k")
            }
            assertContentEquals(e.controlBaselineOffsets, a.controlBaselineOffsets)
        }
    }

    @Test
    fun `strata are identical for shuffled log order`() {
        val items = richWardrobe(tops = 8, bottoms = 8, shoes = 3, outerwear = 2, accessories = 3)
        val logs = mixedLogs()
        assertSameStrata(
            HistoricalStratumBuilder.buildStrata(items, logs),
            HistoricalStratumBuilder.buildStrata(items, logs.shuffled(Random(7))),
        )
    }

    private fun EngineItem.isRole(role: String) = outfitRole == role

    private fun coreOf(items: List<EngineItem>): Set<Long> =
        items.filter { it.outfitRole in OutfitRecommendationEngine.CORE_ROLES }.map { it.id }.toSet()

    @Test
    fun `slot counts match the chosen outfit`() {
        val items = richWardrobe(tops = 6, bottoms = 6, shoes = 3, outerwear = 2, accessories = 4)
        val byId = items.associateBy { it.id }
        val worn = listOf(100L, 200L, 401L, 600L, 602L).map { byId.getValue(it) }
        val controls = HistoricalStratumBuilder.sampleControlOutfits(
            logId = 42L,
            wornItems = worn,
            wornCore = setOf(100L, 200L),
            sameDayWornCores = setOf(setOf(100L, 200L)),
            availableItems = items,
            maxControls = HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM,
        )
        assertEquals(HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM, controls.size)
        for (c in controls) {
            assertEquals(1, c.count { it.isRole("Footwear") }, "shoes in $c")
            assertEquals(2, c.count { it.isRole("Accessory") }, "accessories in $c")
            assertEquals(0, c.count { it.isRole("Outerwear") }, "outerwear in $c")
            assertEquals(c.size, c.map { it.id }.distinct().size, "drawn without replacement")
        }

        // Only 1 accessory in the pool: the worn outfit has it, and so does every control.
        val onlyOneAccessory = items.filter { !it.isRole("Accessory") || it.id == 600L }
        val wornOne = listOf(100L, 200L, 401L, 600L).map { byId.getValue(it) }
        val controlsOne = HistoricalStratumBuilder.sampleControlOutfits(
            logId = 43L,
            wornItems = wornOne,
            wornCore = setOf(100L, 200L),
            sameDayWornCores = setOf(setOf(100L, 200L)),
            availableItems = onlyOneAccessory,
            maxControls = HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM,
        )
        assertTrue(controlsOne.isNotEmpty())
        for (c in controlsOne) {
            assertEquals(listOf(600L), c.filter { it.isRole("Accessory") }.map { it.id })
            assertEquals(1, c.count { it.isRole("Footwear") })
        }
    }

    @Test
    fun `small pools use every candidate once`() {
        val items = richWardrobe(tops = 3, bottoms = 3, onePieces = 2, shoes = 2)
        val byId = items.associateBy { it.id }
        val worn = listOf(100L, 200L, 400L).map { byId.getValue(it) }
        val sameDay = setOf(setOf(100L, 200L), setOf(101L, 201L))
        val controls = HistoricalStratumBuilder.sampleControlOutfits(
            logId = 7L,
            wornItems = worn,
            wornCore = setOf(100L, 200L),
            sameDayWornCores = sameDay,
            availableItems = items,
            maxControls = HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM,
        )
        val expectedCores = buildList {
            for (t in 100L..102L) for (b in 200L..202L) add(setOf(t, b))
            add(setOf(300L))
            add(setOf(301L))
        }.filter { it !in sameDay }
        val actualCores = controls.map { coreOf(it) }
        assertEquals(expectedCores.size, actualCores.size)
        assertEquals(expectedCores.toSet(), actualCores.toSet())
    }

    @Test
    fun `one-piece strata sample one-piece and top-bottom cores alike`() {
        // 6 x 6 = 36 top-bottom cores and 12 one-pieces; the worn one-piece leaves 47 competitors.
        val items = richWardrobe(tops = 6, bottoms = 6, onePieces = 12, shoes = 2)
        val byId = items.associateBy { it.id }
        val worn = listOf(300L, 400L).map { byId.getValue(it) }
        var onePieceControls = 0
        var totalControls = 0
        for (logId in 1L..200L) {
            val controls = HistoricalStratumBuilder.sampleControlOutfits(
                logId = logId,
                wornItems = worn,
                wornCore = setOf(300L),
                sameDayWornCores = setOf(setOf(300L)),
                availableItems = items,
                maxControls = HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM,
            )
            assertEquals(HistoricalStratumBuilder.MAX_CONTROLS_PER_STRATUM, controls.size)
            assertTrue(controls.none { coreOf(it) == setOf(300L) })
            onePieceControls += controls.count { c -> c.any { it.isRole("OnePiece") } }
            totalControls += controls.size
        }
        val share = onePieceControls.toDouble() / totalControls
        assertTrue(abs(share - 11.0 / 47.0) < 0.03, "one-piece share $share, expected about ${11.0 / 47.0}")
    }

    @Test
    fun `strata are identical for shuffled item order`() {
        val items = richWardrobe(tops = 8, bottoms = 8, shoes = 3, outerwear = 2, accessories = 3)
        val logs = mixedLogs()
        assertSameStrata(
            HistoricalStratumBuilder.buildStrata(items, logs),
            HistoricalStratumBuilder.buildStrata(items.shuffled(Random(11)), logs),
        )
    }
}
