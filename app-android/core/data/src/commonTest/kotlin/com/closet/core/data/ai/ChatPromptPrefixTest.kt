package com.closet.core.data.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards the chat prompt contract (issue #72 device bug: an ID in text, a non-matching item returned). */
class ChatPromptPrefixTest {

    private val prompt = ChatPromptPrefix.SYSTEM_PROMPT

    @Test
    fun `prompt no longer asks the model to reference items by numeric ID`() {
        assertFalse(prompt.contains("Use the numeric ID"))
    }

    @Test
    fun `prompt keeps IDs out of text`() {
        assertTrue(prompt.contains("The only valid IDs are the numbers inside [ID:…]."))
        assertTrue(prompt.contains("Never mention IDs"))
        assertTrue(prompt.contains("refer to items by name"))
    }

    @Test
    // No commas in test names: Kotlin/Native (iOS) rejects them.
    fun `prompt says the listed items are closest matches and not guaranteed matches`() {
        assertTrue(prompt.contains("NOT guaranteed matches"))
        assertTrue(prompt.contains("Never attribute a color, material or category"))
    }

    @Test
    fun `prompt tells the model to say plainly when nothing matches`() {
        assertTrue(prompt.contains("If no listed item matches the question"))
        assertTrue(prompt.contains("I don't see any yellow items in your wardrobe."))
        assertTrue(prompt.contains("Never return item_ids for items that don't match"))
    }
}
