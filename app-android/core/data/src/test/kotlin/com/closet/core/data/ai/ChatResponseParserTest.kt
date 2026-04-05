package com.closet.core.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatResponseParserTest {

    // ─── JSON extraction (tested indirectly via parse()) ─────────────────────

    @Test
    fun `plain JSON object parses successfully`() {
        val result = ChatResponseParser.parse("""{"type":"text","text":"hello"}""")
        assertEquals(ChatResponse.Text("hello"), result.getOrNull())
    }

    @Test
    fun `markdown fenced JSON with json tag parses successfully`() {
        val input = "```json\n{\"type\":\"text\",\"text\":\"hello\"}\n```"
        assertEquals(ChatResponse.Text("hello"), ChatResponseParser.parse(input).getOrNull())
    }

    @Test
    fun `markdown fenced JSON without json tag parses successfully`() {
        val input = "```\n{\"type\":\"text\",\"text\":\"hello\"}\n```"
        assertEquals(ChatResponse.Text("hello"), ChatResponseParser.parse(input).getOrNull())
    }

    @Test
    fun `JSON embedded in prose is extracted and parses successfully`() {
        val input = """Here is the answer: {"type":"text","text":"hello"} — hope that helps."""
        assertEquals(ChatResponse.Text("hello"), ChatResponseParser.parse(input).getOrNull())
    }

    @Test
    fun `input with no JSON object returns failure`() {
        assertTrue(ChatResponseParser.parse("no json here at all").isFailure)
    }

    // ─── text type ────────────────────────────────────────────────────────────

    @Test
    fun `text type returns ChatResponse Text`() {
        val result = ChatResponseParser.parse("""{"type":"text","text":"hi there"}""")
        assertEquals(ChatResponse.Text("hi there"), result.getOrNull())
    }

    @Test
    fun `missing text field returns failure`() {
        assertTrue(ChatResponseParser.parse("""{"type":"text"}""").isFailure)
    }

    @Test
    fun `missing type field returns failure`() {
        assertTrue(ChatResponseParser.parse("""{"text":"hello"}""").isFailure)
    }

    // ─── items type ───────────────────────────────────────────────────────────

    @Test
    fun `items type returns ChatResponse WithItems with correct ids and text`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"check these out","item_ids":[1,2,3]}"""
        )
        val response = result.getOrNull() as? ChatResponse.WithItems
        assertNotNull(response)
        assertEquals("check these out", response!!.text)
        assertEquals(listOf(1L, 2L, 3L), response.itemIds)
    }

    @Test
    fun `items type with missing item_ids returns failure`() {
        assertTrue(
            ChatResponseParser.parse("""{"type":"items","text":"here"}""").isFailure
        )
    }

    @Test
    fun `items type with empty item_ids returns failure`() {
        assertTrue(
            ChatResponseParser.parse("""{"type":"items","text":"here","item_ids":[]}""").isFailure
        )
    }

    @Test
    fun `items type with non-long in item_ids returns failure`() {
        assertTrue(
            ChatResponseParser.parse("""{"type":"items","text":"here","item_ids":[1,"bad",3]}""").isFailure
        )
    }

    // ─── outfit type ─────────────────────────────────────────────────────────

    @Test
    fun `outfit type returns ChatResponse WithOutfit with correct fields`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"nice look","item_ids":[10,20,30],"reason":"great combo"}"""
        )
        val response = result.getOrNull() as? ChatResponse.WithOutfit
        assertNotNull(response)
        assertEquals("nice look", response!!.text)
        assertEquals(listOf(10L, 20L, 30L), response.itemIds)
        assertEquals("great combo", response.reason)
    }

    @Test
    fun `outfit type with 2 ids is valid lower bound`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"minimal"}"""
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `outfit type with 4 ids is valid upper bound`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2,3,4],"reason":"full look"}"""
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `outfit type with 1 id returns failure`() {
        assertTrue(
            ChatResponseParser.parse(
                """{"type":"outfit","text":"ok","item_ids":[1],"reason":"solo"}"""
            ).isFailure
        )
    }

    @Test
    fun `outfit type with 5 ids returns failure`() {
        assertTrue(
            ChatResponseParser.parse(
                """{"type":"outfit","text":"ok","item_ids":[1,2,3,4,5],"reason":"too many"}"""
            ).isFailure
        )
    }

    @Test
    fun `outfit type with missing reason returns failure`() {
        assertTrue(
            ChatResponseParser.parse(
                """{"type":"outfit","text":"ok","item_ids":[1,2]}"""
            ).isFailure
        )
    }

    @Test
    fun `outfit type with blank reason returns failure`() {
        assertTrue(
            ChatResponseParser.parse(
                """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"   "}"""
            ).isFailure
        )
    }

    @Test
    fun `outfit type with missing item_ids returns failure`() {
        assertTrue(
            ChatResponseParser.parse(
                """{"type":"outfit","text":"ok","reason":"good"}"""
            ).isFailure
        )
    }

    // ─── unknown type ─────────────────────────────────────────────────────────

    @Test
    fun `unknown type falls back to ChatResponse Text`() {
        val result = ChatResponseParser.parse("""{"type":"widget","text":"fallback"}""")
        assertEquals(ChatResponse.Text("fallback"), result.getOrNull())
    }

    // ─── action — log_outfit ──────────────────────────────────────────────────

    @Test
    fun `log_outfit action on outfit parent with matching ids produces LogOutfit`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"nice","item_ids":[1,2],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1,2]}}"""
        )
        val response = result.getOrNull() as? ChatResponse.WithOutfit
        assertEquals(ChatAction.LogOutfit(listOf(1L, 2L)), response?.action)
    }

    @Test
    fun `log_outfit action on items parent returns null action and parent still succeeds`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2,3],
               "action":{"type":"log_outfit","item_ids":[1,2]}}"""
        )
        val response = result.getOrNull() as? ChatResponse.WithItems
        assertNotNull(response)
        assertNull(response!!.action)
    }

    @Test
    fun `log_outfit action with 1 id returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1]}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    @Test
    fun `log_outfit action with 5 ids returns null action`() {
        // Parent is valid (4 ids); action exceeds the 2-4 size constraint.
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2,3,4],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1,2,3,4,5]}}"""
        )
        assertTrue(result.isSuccess)
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    @Test
    fun `log_outfit action with ids not all in parent returns null action`() {
        // Action ID 99 is not present in the parent outfit's item_ids.
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2,3],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1,2,99]}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    @Test
    fun `log_outfit action with non-long in item_ids returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1,"bad"]}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    @Test
    fun `log_outfit action with non-positive id returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"log_outfit","item_ids":[1,0]}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    @Test
    fun `log_outfit action with missing item_ids returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"log_outfit"}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithOutfit)?.action)
    }

    // ─── action — open_item ───────────────────────────────────────────────────

    @Test
    fun `open_item action with valid id in parent produces OpenItem`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2,3],
               "action":{"type":"open_item","item_id":2}}"""
        )
        assertEquals(
            ChatAction.OpenItem(2L),
            (result.getOrNull() as? ChatResponse.WithItems)?.action
        )
    }

    @Test
    fun `open_item action on outfit parent with valid id produces OpenItem`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"open_item","item_id":1}}"""
        )
        assertEquals(
            ChatAction.OpenItem(1L),
            (result.getOrNull() as? ChatResponse.WithOutfit)?.action
        )
    }

    @Test
    fun `open_item action with missing item_id returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2,3],
               "action":{"type":"open_item"}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }

    @Test
    fun `open_item action with id not in parent returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2,3],
               "action":{"type":"open_item","item_id":99}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }

    @Test
    fun `open_item action with non-positive id returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2,3],
               "action":{"type":"open_item","item_id":0}}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }

    // ─── action — open_recommendations ───────────────────────────────────────

    @Test
    fun `open_recommendations action on items parent produces OpenRecommendations`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1,2],
               "action":{"type":"open_recommendations"}}"""
        )
        assertEquals(
            ChatAction.OpenRecommendations,
            (result.getOrNull() as? ChatResponse.WithItems)?.action
        )
    }

    @Test
    fun `open_recommendations action on outfit parent produces OpenRecommendations`() {
        val result = ChatResponseParser.parse(
            """{"type":"outfit","text":"ok","item_ids":[1,2],"reason":"go",
               "action":{"type":"open_recommendations"}}"""
        )
        assertEquals(
            ChatAction.OpenRecommendations,
            (result.getOrNull() as? ChatResponse.WithOutfit)?.action
        )
    }

    // ─── action error isolation ───────────────────────────────────────────────

    @Test
    fun `unknown action type returns null action and parent succeeds`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1],
               "action":{"type":"teleport_item"}}"""
        )
        assertTrue(result.isSuccess)
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }

    @Test
    fun `missing action field returns null action`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1]}"""
        )
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }

    @Test
    fun `malformed action block returns null action and parent succeeds`() {
        val result = ChatResponseParser.parse(
            """{"type":"items","text":"here","item_ids":[1],
               "action":{"foo":"bar"}}"""
        )
        assertTrue(result.isSuccess)
        assertNull((result.getOrNull() as? ChatResponse.WithItems)?.action)
    }
}
