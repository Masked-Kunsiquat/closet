package com.closet.features.recommendations.engine.learning

import com.closet.features.recommendations.engine.item
import kotlinx.datetime.LocalDate
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
}
