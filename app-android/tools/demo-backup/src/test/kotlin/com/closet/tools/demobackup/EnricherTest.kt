package com.closet.tools.demobackup

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnricherTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun items(n: Int): List<DemoItem> = (1L..n).map { id ->
        val category = Mapping.categoryOrder[(id % Mapping.categoryOrder.size).toInt()]
        DemoItem(id, "Women", "Item $id", category, "Other", null, null, null, File("$id.jpg"))
    }

    private fun run(share: Double, seed: Long = 42, n: Int = 1000): List<ItemDetails> {
        val enricher = Enricher(seed, today, share, minAgeDays = 180)
        return items(n).map(enricher::detailsFor)
    }

    @Test
    fun sameSeedGivesTheSameDetails() {
        assertEquals(run(0.65), run(0.65))
    }

    @Test
    fun aboutTheRequestedShareIsEnrichedAndTheRestIsBare() {
        val enriched = run(0.65).count { !it.isBare }
        assertTrue(enriched in 600..700, "expected ~650 of 1000 enriched, got $enriched")
        assertTrue(run(0.0).all { it.isBare })
        assertTrue(run(1.0).none { it.isBare })
    }

    @Test
    fun purchaseDatesFallBeforeTheWearHistoryAndWithinTwoYears() {
        for (d in run(1.0)) {
            val date = LocalDate.parse(d.purchaseDate)
            assertTrue(!date.isAfter(today.minusDays(180)) && !date.isBefore(today.minusDays(730)), "out of range: $date")
        }
    }

    @Test
    fun theMixIncludesFavoritesDirtyRetiredAndUnbrandedItems() {
        val all = run(1.0)
        assertTrue(all.any { it.favorite })
        assertTrue(all.any { it.dirty })
        assertTrue(all.any { it.retired == "Donated" } && all.any { it.retired == "Sold" })
        assertTrue(all.any { it.brand == null } && all.map { it.brand }.toSet().size > 5)
    }

    @Test
    fun productNamesPickTheirPattern() {
        assertEquals("Checkered", Enricher.patternFor("Femella Women Green Check Shirt"))
        assertEquals("Striped", Enricher.patternFor("Striped Polo"))
        assertEquals(null, Enricher.patternFor("Plain Navy Tee"))
    }
}
