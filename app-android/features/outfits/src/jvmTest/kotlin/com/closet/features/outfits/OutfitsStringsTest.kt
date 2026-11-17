package com.closet.features.outfits

import com.closet.features.outfits.resources.Res
import com.closet.features.outfits.resources.journal_forecast_chip_description
import com.closet.features.outfits.resources.journal_forecast_temp_range
import com.closet.features.outfits.resources.outfits_gallery_item_count
import com.closet.features.outfits.resources.outfits_picker_done_count
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString

class OutfitsStringsTest {

    @Test
    fun pickerDoneCountUsesPositionalArgument() = runTest {
        assertEquals("Done (3)", getString(Res.string.outfits_picker_done_count, 3))
    }

    @Test
    fun galleryItemCountUsesPositionalArgument() = runTest {
        assertEquals("4 items", getString(Res.string.outfits_gallery_item_count, 4))
    }

    @Test
    fun forecastTempRangeUsesTwoArguments() = runTest {
        assertEquals("18° / 9°", getString(Res.string.journal_forecast_temp_range, "18°", "9°"))
    }

    @Test
    fun apostropheIsNotEscaped() = runTest {
        assertEquals(
            "Today's forecast — tap to see 7-day forecast",
            getString(Res.string.journal_forecast_chip_description),
        )
    }
}
