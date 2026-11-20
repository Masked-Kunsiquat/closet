package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.brand_management_delete_blocked_message
import com.closet.features.wardrobe.resources.bulk_wash_apply_error
import com.closet.features.wardrobe.resources.wardrobe_redetect_none
import com.closet.features.wardrobe.resources.wardrobe_redetect_share
import com.closet.features.wardrobe.resources.wardrobe_worn_times
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class WardrobeStringsTest {

    /** Plural rules follow the default locale, so pin English for the plural assertions. */
    private suspend fun inEnglish(block: suspend () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun wornTimesPluralUsesSingularAndPlural() = runTest {
        inEnglish {
            assertEquals("Worn 1 time", getPluralString(Res.plurals.wardrobe_worn_times, 1, 1))
            assertEquals("Worn 3 times", getPluralString(Res.plurals.wardrobe_worn_times, 3, 3))
        }
    }

    @Test
    fun brandBlockedMessageUsesPositionalArgumentsAndPlainQuotes() = runTest {
        inEnglish {
            assertEquals(
                "\"Acme\" is used by 2 items. Reassign them before deleting.",
                getPluralString(Res.plurals.brand_management_delete_blocked_message, 2, "Acme", 2),
            )
        }
    }

    @Test
    fun bulkWashApplyErrorKeepsPlainApostrophe() = runTest {
        assertEquals("Couldn't update some items. Please try again.", getString(Res.string.bulk_wash_apply_error))
    }

    @Test
    fun redetectStringsFormatShareAndKeepPlainApostrophe() = runTest {
        assertEquals("62%", getString(Res.string.wardrobe_redetect_share, 62))
        assertEquals("Couldn't detect colors", getString(Res.string.wardrobe_redetect_none))
    }
}
