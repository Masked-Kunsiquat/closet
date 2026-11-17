package com.closet.features.recommendations

import com.closet.features.recommendations.resources.Res
import com.closet.features.recommendations.resources.recs_occasion_sheet_title
import com.closet.features.recommendations.resources.recs_page_indicator_description
import com.closet.features.recommendations.resources.recs_style_vibe_label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString

class RecommendationsStringsTest {

    @Test
    fun styleVibeLabelUsesPositionalArgument() = runTest {
        assertEquals("Style: Minimalist", getString(Res.string.recs_style_vibe_label, "Minimalist"))
    }

    @Test
    fun pageIndicatorUsesTwoArguments() = runTest {
        assertEquals("Page 2 of 3", getString(Res.string.recs_page_indicator_description, 2, 3))
    }

    @Test
    fun occasionTitleRendersApostrophe() = runTest {
        assertEquals("What's the occasion?", getString(Res.string.recs_occasion_sheet_title))
    }
}
