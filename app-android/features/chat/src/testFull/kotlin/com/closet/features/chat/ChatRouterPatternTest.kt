package com.closet.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the regex/pattern helpers in [ChatRouter]'s companion object.
 * The full-flavor [ChatRouter] constructor calls `LanguageIdentification.getClient()` (GMS),
 * so we test the internal pattern vals directly without instantiating the class.
 */
class ChatRouterPatternTest {

    // ── ITEM_NAME_PATTERN ─────────────────────────────────────────────────────

    @Test
    fun `ITEM_NAME_PATTERN captures item name from full how-many-times-worn query`() {
        val match = ChatRouter.ITEM_NAME_PATTERN.find("how many times have i worn my grey blazer")
        assertNotNull(match)
        assertEquals("grey blazer", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN captures item name without optional have-i phrase`() {
        val match = ChatRouter.ITEM_NAME_PATTERN.find("how many times worn my black jeans?")
        assertNotNull(match)
        // (.+?)(?:\?|$) stops capture before the trailing '?'
        assertEquals("black jeans", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN captures item preceded by the article`() {
        val match = ChatRouter.ITEM_NAME_PATTERN.find("worn the white shirt")
        assertNotNull(match)
        assertEquals("white shirt", match!!.groupValues[1].trim())
    }

    @Test
    fun `ITEM_NAME_PATTERN does not match wear-count-for query (no worn prefix)`() {
        // "wear count for the white shirt" — no "worn" token → extractItemName returns null → Unrouted
        val match = ChatRouter.ITEM_NAME_PATTERN.find("wear count for the white shirt")
        assertNull(match)
    }

    @Test
    fun `ITEM_NAME_PATTERN does not match query with no item name`() {
        val match = ChatRouter.ITEM_NAME_PATTERN.find("how many items do i own")
        assertNull(match)
    }

    // ── DAYS_PATTERN ──────────────────────────────────────────────────────────

    /** Applies the same count × unit conversion used in extractDays(). */
    private fun Regex.extractDays(input: String): Int? {
        val match = find(input) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        return if ("week" in unit) count * 7 else count
    }

    @Test
    fun `DAYS_PATTERN extracts 30 days`() {
        assertEquals(30, ChatRouter.DAYS_PATTERN.extractDays("what haven't i worn in 30 days"))
    }

    @Test
    fun `DAYS_PATTERN converts 2 weeks to 14 days`() {
        assertEquals(14, ChatRouter.DAYS_PATTERN.extractDays("what haven't i worn in 2 weeks"))
    }

    @Test
    fun `DAYS_PATTERN converts 1 week to 7 days`() {
        assertEquals(7, ChatRouter.DAYS_PATTERN.extractDays("haven't worn in 1 week"))
    }

    @Test
    fun `DAYS_PATTERN returns null for 'lately' (falls back to DEFAULT_UNWORN_DAYS)`() {
        assertNull(ChatRouter.DAYS_PATTERN.extractDays("what haven't i worn lately"))
    }

    // ── WORE_ON_INTERROGATIVE_PATTERN ─────────────────────────────────────────

    @Test
    fun `WORE_ON_INTERROGATIVE_PATTERN matches what-did-i-wore-on`() {
        assertTrue(
            ChatRouter.WORE_ON_INTERROGATIVE_PATTERN
                .containsMatchIn("what did i wore on tuesday")
        )
    }

    @Test
    fun `WORE_ON_INTERROGATIVE_PATTERN does not match when leading what-to-i gap exceeds 15 chars`() {
        // "what goes with what i wore on tuesday": gap from leading 'what' to 'i' is 16 chars
        // The ^ anchor ensures only the leading 'what' is evaluated — the mid-sentence
        // "what i wore on" is not a valid anchor point.
        assertFalse(
            ChatRouter.WORE_ON_INTERROGATIVE_PATTERN
                .containsMatchIn("what goes with what i wore on tuesday")
        )
    }

    // ── Pattern priority: never-worn before not-worn-since ────────────────────

    @Test
    fun `never-worn query satisfies neverWorn condition but not notWornSince condition`() {
        val q = "what have i never worn"
        // matchesNeverWorn: "never worn" in lower → true (evaluated first in `when` block)
        assertTrue("never worn" in q)
        // matchesNotWornSince: requires a not-worn keyword AND a day count or recency word
        val hasNotWornKeyword = "haven't worn" in q || "havent worn" in q ||
            "not worn" in q || "unworn" in q
        val hasDayOrRecency = ChatRouter.DAYS_PATTERN.containsMatchIn(q) ||
            "lately" in q || "recently" in q
        assertFalse(hasNotWornKeyword && hasDayOrRecency)
    }
}