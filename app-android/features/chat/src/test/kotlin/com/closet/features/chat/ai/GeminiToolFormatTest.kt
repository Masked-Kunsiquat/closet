package com.closet.features.chat.ai

import com.closet.core.data.ai.tools.ToolCall
import com.closet.core.data.ai.tools.ToolCallResult
import com.closet.core.data.ai.tools.ToolParam
import com.closet.core.data.ai.tools.ToolParamType
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiToolFormatTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val spec = ToolSpec(
        "find_items", "Find items",
        listOf(
            ToolParam("query", ToolParamType.STRING, "q", required = true),
            ToolParam("tags", ToolParamType.STRING_ARRAY, "t"),
            ToolParam("season", ToolParamType.STRING, "s", enumValues = listOf("summer", "winter")),
        ),
    )
    private val noArgs = ToolSpec("ping", "Ping", emptyList())

    private fun request(allowTools: Boolean, turns: List<ToolTurn> = listOf(ToolTurn.User("hi"))) =
        GeminiToolFormat.buildRequest("sys", turns, listOf(spec, noArgs), allowTools)

    @Test
    fun `tools on declares functions and sends no mime type`() {
        val req = request(true)
        assertNull(req["generationConfig"])
        assertNull(req["toolConfig"])
        val decls = req["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        val fn = decls[0].jsonObject
        assertEquals("find_items", fn["name"]!!.jsonPrimitive.content)
        val params = fn["parameters"]!!.jsonObject
        assertEquals(listOf("query"), params["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("array", params["properties"]!!.jsonObject["tags"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(2, params["properties"]!!.jsonObject["season"]!!.jsonObject["enum"]!!.jsonArray.size)
        assertNull(decls[1].jsonObject["parameters"])
        assertEquals(
            "sys",
            req["system_instruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `tools off omits tools and sends JSON mime type`() {
        val req = request(false)
        assertNull(req["tools"])
        assertEquals(
            "application/json",
            req["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `history maps functionCall and functionResponse parts`() {
        val args = buildJsonObject { put("query", "yellow") }
        val turns = listOf(
            ToolTurn.User("any yellow"),
            ToolTurn.Assistant(null, listOf(ToolCall("find_items#0.0", "find_items", args))),
            ToolTurn.ToolResults(
                listOf(
                    ToolCallResult("find_items#0.0", "find_items", buildJsonObject { put("n", 1) }),
                    ToolCallResult("ping#0.1", "ping", JsonArray(listOf(JsonPrimitive(1)))),
                ),
            ),
        )
        val contents = request(true, turns)["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        val model = contents[1].jsonObject
        assertEquals("model", model["role"]!!.jsonPrimitive.content)
        val fc = model["parts"]!!.jsonArray[0].jsonObject["functionCall"]!!.jsonObject
        assertEquals("find_items", fc["name"]!!.jsonPrimitive.content)
        assertEquals("yellow", fc["args"]!!.jsonObject["query"]!!.jsonPrimitive.content)
        val resp = contents[2].jsonObject
        assertEquals("user", resp["role"]!!.jsonPrimitive.content)
        val parts = resp["parts"]!!.jsonArray
        assertEquals(
            "1",
            parts[0].jsonObject["functionResponse"]!!.jsonObject["response"]!!.jsonObject["n"]!!.jsonPrimitive.content,
        )
        val wrapped = parts[1].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("ping", wrapped["name"]!!.jsonPrimitive.content)
        assertEquals(1, wrapped["response"]!!.jsonObject["result"]!!.jsonArray.size)
    }

    @Test
    fun `response with functionCall parts yields Calls with unique ids`() {
        val body = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"find_items","args":{"query":"x"}}},
            {"functionCall":{"name":"find_items","args":{}}}]}}]}"""
        val first = GeminiToolFormat.parseResponse(body, json, 0) as ToolStep.Calls
        val second = GeminiToolFormat.parseResponse(body, json, 1) as ToolStep.Calls
        assertEquals(listOf("find_items#0.0", "find_items#0.1"), first.calls.map { it.id })
        assertEquals("find_items#1.0", second.calls[0].id)
        assertEquals("x", first.calls[0].args["query"]!!.jsonPrimitive.content)
    }

    @Test
    fun `response with text yields Final`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""
        assertEquals(ToolStep.Final("hello"), GeminiToolFormat.parseResponse(body, json, 0))
    }

    @Test
    fun `round counts model turns with calls`() {
        val turns = listOf(
            ToolTurn.User("a"),
            ToolTurn.Assistant("plain"),
            ToolTurn.User("b"),
            ToolTurn.Assistant(null, listOf(ToolCall("i", "ping", buildJsonObject {}))),
        )
        assertEquals(1, GeminiToolFormat.roundOf(turns))
    }

    @Test
    fun `empty candidates is an error`() {
        assertTrue(runCatching { GeminiToolFormat.parseResponse("""{"candidates":[]}""", json, 0) }.isFailure)
    }

    private val realisticHistory = listOf(
        ToolTurn.User("any yellow"),
        ToolTurn.Assistant(null, listOf(ToolCall("find_items#0.0", "find_items", buildJsonObject { put("query", "yellow") }))),
        ToolTurn.ToolResults(listOf(ToolCallResult("find_items#0.0", "find_items", buildJsonObject { put("n", 1) }))),
    )

    @Test
    fun `tools off flattens tool history to text with no function parts`() {
        val req = request(false, realisticHistory)
        val text = req.toString()
        assertTrue(!text.contains("functionCall") && !text.contains("functionResponse"))
        assertNull(req["tools"])
        assertEquals("application/json", req["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
        val contents = req["contents"]!!.jsonArray
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(
            "Called find_items with {\"query\":\"yellow\"}",
            contents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals("user", contents[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(
            "Result of find_items: {\"n\":1}",
            contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `thoughtSignature is kept on parse and re-emitted on history`() {
        val body = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"ping","args":{}},"thoughtSignature":"sig123"},
            {"functionCall":{"name":"ping","args":{}}}]}}]}"""
        val calls = (GeminiToolFormat.parseResponse(body, json, 0) as ToolStep.Calls).calls
        assertEquals("sig123", calls[0].providerData!!["thoughtSignature"]!!.jsonPrimitive.content)
        assertNull(calls[1].providerData)
        val contents = request(true, listOf(ToolTurn.User("a"), ToolTurn.Assistant(null, calls)))["contents"]!!.jsonArray
        val parts = contents[1].jsonObject["parts"]!!.jsonArray
        assertEquals("sig123", parts[0].jsonObject["thoughtSignature"]!!.jsonPrimitive.content)
        assertNull(parts[1].jsonObject["thoughtSignature"])
    }

    @Test
    fun `server function call id round-trips on call and response parts`() {
        val body = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"id":"abc","name":"ping","args":{}}}
        ]}}]}"""
        val calls = (GeminiToolFormat.parseResponse(body, json, 0) as ToolStep.Calls).calls
        assertEquals("abc", calls[0].id)
        val turns = listOf(
            ToolTurn.User("hi"),
            ToolTurn.Assistant(null, calls),
            ToolTurn.ToolResults(listOf(ToolCallResult("abc", "ping", JsonPrimitive("ok")))),
        )
        val contents = request(true, turns)["contents"]!!.jsonArray
        val fc = contents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionCall"]!!.jsonObject
        val fr = contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("abc", fc["id"]!!.jsonPrimitive.content)
        assertEquals("abc", fr["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `synthetic ids are not sent on the wire`() {
        val body = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"ping","args":{}}}]}}]}"""
        val calls = (GeminiToolFormat.parseResponse(body, json, 0) as ToolStep.Calls).calls
        assertEquals("ping#0.0", calls[0].id)
        val turns = listOf(
            ToolTurn.User("hi"),
            ToolTurn.Assistant(null, calls),
            ToolTurn.ToolResults(listOf(ToolCallResult(calls[0].id, "ping", JsonPrimitive("ok")))),
        )
        val contents = request(true, turns)["contents"]!!.jsonArray
        val fc = contents[1].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionCall"]!!.jsonObject
        val fr = contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertNull(fc["id"])
        assertNull(fr["id"])
    }
}
