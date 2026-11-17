package com.closet.features.chat

import com.closet.features.chat.resources.Res
import com.closet.features.chat.resources.chat_action_alternatives
import com.closet.features.chat.resources.chat_powered_by
import com.closet.features.chat.resources.chat_welcome_suggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

class ChatStringsTest {

    @Test
    fun poweredByUsesPositionalArgument() = runTest {
        assertEquals("Powered by Gemini", getString(Res.string.chat_powered_by, "Gemini"))
    }

    @Test
    fun welcomeSuggestionsHaveFourEntriesWithPlainApostrophe() = runTest {
        val suggestions = getStringArray(Res.array.chat_welcome_suggestions)
        assertEquals(4, suggestions.size)
        assertEquals("What haven't I worn lately?", suggestions[1])
    }

    @Test
    fun alternativesActionKeepsArrow() = runTest {
        assertEquals("Alternatives →", getString(Res.string.chat_action_alternatives))
    }
}
