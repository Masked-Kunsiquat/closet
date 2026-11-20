package com.closet.features.settings

import com.closet.features.settings.resources.Res
import com.closet.features.settings.resources.settings_accent_selected
import com.closet.features.settings.resources.settings_ai_nano_downloading
import com.closet.features.settings.resources.settings_ai_nano_failed
import com.closet.features.settings.resources.settings_image_compress_result_with_failures
import com.closet.features.settings.resources.settings_image_storage_used_summary
import com.closet.features.settings.resources.settings_section_ai_tools
import com.closet.features.settings.resources.settings_wardrobe_color_redetect_confirm_count
import com.closet.features.settings.resources.settings_wardrobe_color_redetect_result
import com.closet.features.settings.resources.settings_wardrobe_search_index_summary
import com.closet.features.settings.resources.settings_weather_enabled_summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class SettingsStringsTest {

    @Test
    fun nanoDownloadingFormatsPercentage() = runTest {
        assertEquals("Downloading model (42%)…", getString(Res.string.settings_ai_nano_downloading, 42))
    }

    @Test
    fun accentSelectedUsesPositionalString() = runTest {
        assertEquals("Amber, selected", getString(Res.string.settings_accent_selected, "Amber"))
    }

    @Test
    fun nanoFailedUsesPositionalString() = runTest {
        assertEquals("Setup failed: boom", getString(Res.string.settings_ai_nano_failed, "boom"))
    }

    @Test
    fun storageUsedSummaryUsesPositionalString() = runTest {
        assertEquals(
            "1.5 MB used by wardrobe images",
            getString(Res.string.settings_image_storage_used_summary, "1.5 MB"),
        )
    }

    @Test
    fun apostropheIsNotEscaped() = runTest {
        assertEquals(
            "Fetch today's forecast using your location",
            getString(Res.string.settings_weather_enabled_summary),
        )
    }

    @Test
    fun pluralWithThreeArguments() = runTest {
        assertEquals(
            "Compressed 3 images, 2 failed (1 skipped)",
            getPluralString(Res.plurals.settings_image_compress_result_with_failures, 3, 3, 1, 2),
        )
    }

    @Test
    fun ampersandIsUnescaped() = runTest {
        assertEquals("AI & Image Tools", getString(Res.string.settings_section_ai_tools))
    }

    @Test
    fun singleArgumentPluralOne() = runTest {
        assertEquals(
            "1 item indexed",
            getPluralString(Res.plurals.settings_wardrobe_search_index_summary, 1, 1),
        )
    }

    @Test
    fun colorRedetectResultHasThreeCounts() = runTest {
        assertEquals(
            "Re-detected colours on 4 items; 1 unchanged; 2 failed",
            getPluralString(Res.plurals.settings_wardrobe_color_redetect_result, 4, 4, 1, 2),
        )
        assertEquals(
            "Re-detected colours on 1 item; 0 unchanged; 0 failed",
            getPluralString(Res.plurals.settings_wardrobe_color_redetect_result, 1, 1, 0, 0),
        )
    }

    @Test
    fun colorRedetectConfirmCount() = runTest {
        assertEquals(
            "This will re-detect colours on 5 items.",
            getPluralString(Res.plurals.settings_wardrobe_color_redetect_confirm_count, 5, 5),
        )
    }
}
