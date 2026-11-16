package com.closet.features.stats

import com.closet.features.stats.resources.Res
import com.closet.features.stats.resources.stats_cd_worn_pct
import com.closet.features.stats.resources.stats_cost_per_wear_formatted
import com.closet.features.stats.resources.stats_section_never_worn_count
import com.closet.features.stats.resources.stats_total_logs
import com.closet.features.stats.resources.stats_wear_badge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class StatsStringsTest {

    @Test
    fun wornPctUsesPositionalArgs() = runTest {
        assertEquals("3 of 10 items worn", getString(Res.string.stats_cd_worn_pct, 3, 10))
    }

    @Test
    fun costPerWearFormatted() = runTest {
        assertEquals("$2.50 / wear", getString(Res.string.stats_cost_per_wear_formatted, "$2.50"))
    }

    @Test
    fun totalLogsPluralOne() = runTest {
        assertEquals("You've logged 1 outfit", getPluralString(Res.plurals.stats_total_logs, 1, 1))
    }

    @Test
    fun totalLogsPluralOther() = runTest {
        assertEquals("You've logged 4 outfits", getPluralString(Res.plurals.stats_total_logs, 4, 4))
    }

    @Test
    fun wearBadgePlural() = runTest {
        assertEquals("2 wears", getPluralString(Res.plurals.stats_wear_badge, 2, 2))
    }

    @Test
    fun neverWornCount() = runTest {
        assertEquals("Never Worn (5)", getString(Res.string.stats_section_never_worn_count, 5))
    }
}
