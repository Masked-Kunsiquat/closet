package com.closet.platform.gms.chat

import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.ConversationTurn.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoChatPromptTest {

    // ── trimOldest ───────────────────────────────────────────────────────────

    @Test
    fun `trimOldest keeps everything when it fits`() {
        val turns = listOf(1, 2, 3, 4)
        assertEquals(turns, trimOldest(turns) { true })
    }

    @Test
    fun `trimOldest drops oldest turns until it fits`() {
        val turns = listOf(1, 2, 3, 4, 5, 6)
        assertEquals(listOf(4, 5, 6), trimOldest(turns) { it.size <= 3 })
    }

    @Test
    fun `trimOldest returns empty when even zero turns do not fit`() {
        var calls = 0
        val result = trimOldest(listOf(1, 2, 3)) { calls++; false }
        assertTrue(result.isEmpty())
        // 3, 2, 1 turns tried; zero turns is sent without asking again.
        assertEquals(3, calls)
    }

    @Test
    fun `trimOldest on empty input never calls fits`() {
        val result = trimOldest(emptyList<Int>()) { error("should not be called") }
        assertTrue(result.isEmpty())
    }

    // ── nanoTurns ────────────────────────────────────────────────────────────

    private val history = listOf(
        ConversationTurn(Role.User, "q1"),
        ConversationTurn(Role.Assistant, "a1"),
        ConversationTurn(Role.User, "q2"),
        ConversationTurn(Role.Assistant, "a2"),
    )

    @Test
    fun `nanoTurns maps history oldest first then the final user turn`() {
        val turns = nanoTurns(history, context = "CTX", userMessage = "hi", inlineSystemPrompt = null)
        assertEquals(
            listOf(
                NanoTurn(isUser = true, text = "q1"),
                NanoTurn(isUser = false, text = "a1"),
                NanoTurn(isUser = true, text = "q2"),
                NanoTurn(isUser = false, text = "a2"),
                NanoTurn(isUser = true, text = "Context:\nCTX\n\nhi"),
            ),
            turns,
        )
    }

    @Test
    fun `nanoTurns prepends the inline system prompt to the first user turn`() {
        val turns = nanoTurns(history, context = "CTX", userMessage = "hi", inlineSystemPrompt = "SYS")
        assertEquals("SYS\n\nq1", turns.first().text)
        // Only the first user turn gets it.
        assertEquals("Context:\nCTX\n\nhi", turns.last().text)
    }

    @Test
    fun `nanoTurns with no history puts the inline system prompt on the final turn`() {
        val turns = nanoTurns(emptyList(), context = "CTX", userMessage = "hi", inlineSystemPrompt = "SYS")
        assertEquals(listOf(NanoTurn(isUser = true, text = "SYS\n\nContext:\nCTX\n\nhi")), turns)
    }

    @Test
    fun `nanoTurns drops a leading assistant turn left by trimming`() {
        val turns = nanoTurns(history.drop(1), context = "CTX", userMessage = "hi", inlineSystemPrompt = "SYS")
        assertEquals(NanoTurn(isUser = true, text = "SYS\n\nq2"), turns.first())
        assertEquals(4 - 1, turns.size)
    }
}
