package com.closet.platform.gms.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoChatReplyTest {

    @Test
    fun `blank reply is empty and triggers the plain-text fallback`() {
        val blank = NanoChatReply(type = "text", text = "  ", itemIds = emptyList(), reason = null)
        assertTrue(blank.isEmpty())
        // typedResponseOrNull returning null is what makes NanoChatProvider fall back to plain text.
        assertEquals(null, com.closet.platform.gms.typedResponseOrNull(listOf(blank)) { it.isEmpty() })
    }

    @Test
    fun `reply with text or item IDs is not empty`() {
        val text = NanoChatReply(type = "text", text = "Hi", itemIds = emptyList(), reason = null)
        val items = NanoChatReply(type = "items", text = "", itemIds = listOf(2L), reason = null)
        assertEquals(false, text.isEmpty())
        assertEquals(false, items.isEmpty())
        assertEquals(text, com.closet.platform.gms.typedResponseOrNull(listOf(text)) { it.isEmpty() })
    }

    @Test
    fun `text reply becomes ChatResponse Text`() {
        val reply = NanoChatReply(type = "text", text = "Hello", itemIds = emptyList(), reason = null)
        assertEquals(ChatResponse.Text("Hello"), reply.toChatResponse().getOrThrow())
    }

    @Test
    fun `items reply becomes ChatResponse WithItems`() {
        val reply = NanoChatReply(type = "items", text = "These", itemIds = listOf(3L, 7L), reason = null)
        assertEquals(ChatResponse.WithItems("These", listOf(3L, 7L)), reply.toChatResponse().getOrThrow())
    }

    @Test
    fun `outfit reply becomes ChatResponse WithOutfit`() {
        val reply = NanoChatReply(type = "outfit", text = "Wear", itemIds = listOf(1L, 2L, 3L), reason = "Warm")
        assertEquals(
            ChatResponse.WithOutfit("Wear", listOf(1L, 2L, 3L), "Warm"),
            reply.toChatResponse().getOrThrow(),
        )
    }

    @Test
    fun `parser validation still applies to typed replies`() {
        // An outfit without a reason is rejected by ChatResponseParser, as for plain text.
        val reply = NanoChatReply(type = "outfit", text = "Wear", itemIds = listOf(1L, 2L), reason = null)
        assertTrue(reply.toChatResponse().exceptionOrNull() is ChatResponseParseException)
        // So is an items reply with no ids.
        val empty = NanoChatReply(type = "items", text = "None", itemIds = emptyList(), reason = null)
        assertTrue(empty.toChatResponse().exceptionOrNull() is ChatResponseParseException)
    }

    @Test
    fun `parser JSON uses the snake_case keys the parser expects and escapes text`() {
        val reply = NanoChatReply(type = "outfit", text = "a \"b\"", itemIds = listOf(1L, 2L), reason = "r")
        assertEquals(
            """{"type":"outfit","text":"a \"b\"","item_ids":[1,2],"reason":"r"}""",
            reply.toParserJson(),
        )
    }
}
