package com.closet.features.chat.ai

import com.closet.core.data.ai.tools.ToolCall
import com.closet.core.data.ai.tools.ToolCallResult
import com.closet.core.data.ai.tools.ToolParam
import com.closet.core.data.ai.tools.ToolParamType
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class AnthropicToolFormatTest {
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
        AnthropicToolFormat.buildRequest("m", "sys", turns, listOf(spec, noArgs), allowTools, 1024)

    @Test
    fun `request declares tools with input_schema`() {
        val req = request(true)
        assertEquals("sys", req["system"]!!.jsonPrimitive.content)
        assertNull(req["tool_choice"])
        val tool = req["tools"]!!.jsonArray[0].jsonObject
        assertEquals("find_items", tool["name"]!!.jsonPrimitive.content)
        val schema = tool["input_schema"]!!.jsonObject
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("query"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val props = schema["properties"]!!.jsonObject
        assertEquals("string", props["tags"]!!.jsonObject["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(2, props["season"]!!.jsonObject["enum"]!!.jsonArray.size)
        assertNull(req["tools"]!!.jsonArray[1].jsonObject["input_schema"]!!.jsonObject["required"])
    }

    @Test
    fun `tools off keeps declarations and sets tool_choice none`() {
        val req = request(false)
        assertEquals(2, req["tools"]!!.jsonArray.size)
        assertEquals("none", req["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `history maps tool_use and tool_result blocks`() {
        val args = buildJsonObject { put("query", "yellow") }
        val turns = listOf(
            ToolTurn.User("any yellow"),
            ToolTurn.Assistant("looking", listOf(ToolCall("tu1", "find_items", args))),
            ToolTurn.ToolResults(listOf(ToolCallResult("tu1", "find_items", buildJsonObject { put("n", 1) }))),
        )
        val msgs = request(true, turns)["messages"]!!.jsonArray
        assertEquals(3, msgs.size)
        val assistant = msgs[1].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val blocks = assistant["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        val use = blocks[1].jsonObject
        assertEquals("tool_use", use["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", use["id"]!!.jsonPrimitive.content)
        assertEquals("yellow", use["input"]!!.jsonObject["query"]!!.jsonPrimitive.content)
        val user = msgs[2].jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        val result = user["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", result["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("{\"n\":1}", result["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant turn without calls is plain text`() {
        val msgs = request(true, listOf(ToolTurn.User("a"), ToolTurn.Assistant("b")))["messages"]!!.jsonArray
        assertEquals(JsonPrimitive("b"), msgs[1].jsonObject["content"])
    }

    @Test
    fun `response with tool_use yields Calls with ids`() {
        val body = """{"content":[{"type":"text","text":"ok"},
            {"type":"tool_use","id":"toolu_1","name":"find_items","input":{"query":"x"}},
            {"type":"tool_use","id":"toolu_2","name":"ping","input":{}}],"stop_reason":"tool_use"}"""
        val step = AnthropicToolFormat.parseResponse(body, json) as ToolStep.Calls
        assertEquals(listOf("toolu_1", "toolu_2"), step.calls.map { it.id })
        assertEquals("x", step.calls[0].args["query"]!!.jsonPrimitive.content)
    }

    @Test
    fun `non-object input becomes an args parse error and missing input stays empty`() {
        val body = """{"content":[
            {"type":"tool_use","id":"a","name":"ping","input":"oops"},
            {"type":"tool_use","id":"b","name":"ping","input":[1]},
            {"type":"tool_use","id":"c","name":"ping"},
            {"type":"tool_use","id":"d","name":"ping","input":null}]}"""
        val calls = (AnthropicToolFormat.parseResponse(body, json) as ToolStep.Calls).calls
        assertTrue(calls[0].args.containsKey(com.closet.core.data.ai.tools.ARGS_PARSE_ERROR_KEY))
        assertTrue(calls[1].args.containsKey(com.closet.core.data.ai.tools.ARGS_PARSE_ERROR_KEY))
        assertTrue(calls[2].args.isEmpty())
        assertTrue(calls[3].args.isEmpty())
    }

    @Test
    fun `response with text blocks yields Final`() {
        val body = """{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}],"stop_reason":"end_turn"}"""
        assertEquals(ToolStep.Final("ab"), AnthropicToolFormat.parseResponse(body, json))
    }

    @Test
    fun `empty response is an error`() {
        val failed = runCatching { AnthropicToolFormat.parseResponse("""{"content":[]}""", json) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `tools off with realistic history sends tools and tool_choice none`() {
        val args = buildJsonObject { put("query", "yellow") }
        val turns = listOf(
            ToolTurn.User("any yellow"),
            ToolTurn.Assistant(null, listOf(ToolCall("tu1", "find_items", args))),
            ToolTurn.ToolResults(listOf(ToolCallResult("tu1", "find_items", buildJsonObject { put("n", 1) }))),
        )
        val req = request(false, turns)
        assertEquals(2, req["tools"]!!.jsonArray.size)
        assertEquals("none", req["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(req["messages"]!!.jsonArray[1].toString().contains("tool_use"))
    }
}
