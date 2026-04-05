package com.closet.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChatRouterPatterns] — the regex constants and pure-logic matcher/parser
 * functions that back [ChatRouter]'s routing decisions.
 *
 * The full-flavor [ChatRouter] constructor calls `LanguageIdentification.getClient()` (GMS),
 * so routing logic lives in [ChatRouterPatterns] and is tested here without instantiating
 * [ChatRouter].
 */
class ChatRouterPatternTest {

    // ── ITEM_NAME_PATTERN ─────────────────────────────────────────────────────

    @Test
    fun `ITEM_NAME_PATTERN captures item name from full how-many-times-worn query`() {
        val match = ChatRouterPatterns.ITEM_NAME_PATTERN.find("how many times have i worn my grey blazer")
        assertNotNull(match)
        assertEquals("grey blazer", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN captures item name without optional have-i phrase`() {
        val match = ChatRouterPatterns.ITEM_NAME_PATTERN.find("how many times worn my black jeans?")
        assertNotNull(match)
        // (.+?)(?:\?|$) stops capture before the trailing '?'
        assertEquals("black jeans", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN captures item preceded by the article`() {
        val match = ChatRouterPatterns.ITEM_NAME_PATTERN.find("worn the white shirt")
        assertNotNull(match)
        assertEquals("white shirt", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN does not match wear-count-for query (no worn prefix)`() {
        // "wear count for the white shirt" — no "worn" token → matchItemName returns null → Unrouted
        val match = ChatRouterPatterns.ITEM_NAME_PATTERN.find("wear count for the white shirt")
        assertNull(match)
    }

    @Test
    fun `ITEM_NAME_PATTERN does not match query with no item name`() {
        val match = ChatRouterPatterns.ITEM_NAME_PATTERN.find("how many items do i own")
        assertNull(match)
    }

    // ── DAYS_PATTERN ──────────────────────────────────────────────────────────

    @Test
    fun `DAYS_PATTERN extracts 30 days`() {
        assertEquals(30, ChatRouterPatterns.parseDays("what haven't i worn in 30 days"))
    }

    @Test
    fun `DAYS_PATTERN converts 2 weeks to 14 days`() {
        assertEquals(14, ChatRouterPatterns.parseDays("what haven't i worn in 2 weeks"))
    }

    @Test
    fun `DAYS_PATTERN converts 1 week to 7 days`() {
        assertEquals(7, ChatRouterPatterns.parseDays("haven't worn in 1 week"))
    }

    @Test
    fun `DAYS_PATTERN returns null for 'lately' (falls back to DEFAULT_UNWORN_DAYS)`() {
        assertNull(ChatRouterPatterns.parseDays("what haven't i worn lately"))
    }

    // ── WORE_ON_INTERROGATIVE_PATTERN ─────────────────────────────────────────

    @Test
    fun `WORE_ON_INTERROGATIVE_PATTERN matches what-did-i-wore-on`() {
        assertTrue(
            ChatRouterPatterns.WORE_ON_INTERROGATIVE_PATTERN
                .containsMatchIn("what did i wore on tuesday")
        )
    }

    @Test
    fun `WORE_ON_INTERROGATIVE_PATTERN does not match when leading what-to-i gap exceeds 15 chars`() {
        // "what goes with what i wore on tuesday": gap from leading 'what' to 'i' is 16 chars
        // The ^ anchor ensures only the leading 'what' is evaluated — the mid-sentence
        // "what i wore on" is not a valid anchor point.
        assertFalse(
            ChatRouterPatterns.WORE_ON_INTERROGATIVE_PATTERN
                .containsMatchIn("what goes with what i wore on tuesday")
        )
    }

    // ── Pattern priority: never-worn before not-worn-since ────────────────────

    @Test
    fun `never-worn query satisfies matchesNeverWorn but not matchesNotWornSince`() {
        val q = "what have i never worn"
        // matchesNeverWorn is evaluated first in ChatRouter's `when` block
        assertTrue(ChatRouterPatterns.matchesNeverWorn(q))
        // matchesNotWornSince requires a not-worn keyword AND a day count or recency word
        assertFalse(ChatRouterPatterns.matchesNotWornSince(q))
    }
}
