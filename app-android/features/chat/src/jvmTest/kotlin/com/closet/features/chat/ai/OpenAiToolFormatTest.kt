package com.closet.features.chat.ai

import com.closet.core.data.ai.tools.ToolCall
import com.closet.core.data.ai.tools.ToolCallResult
import com.closet.core.data.ai.tools.ToolParam
import com.closet.core.data.ai.tools.ToolParamType
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.closet.core.data.ai.tools.ToolsUnsupportedException
import com.closet.core.data.di.AiHttpClient
import com.closet.core.data.repository.AiPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class OpenAiToolFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val spec = ToolSpec(
        "find_items", "Find items",
        listOf(
            ToolParam("query", ToolParamType.STRING, "q", required = true),
            ToolParam("limit", ToolParamType.INTEGER, "n"),
            ToolParam("tags", ToolParamType.STRING_ARRAY, "t"),
            ToolParam("season", ToolParamType.STRING, "s", enumValues = listOf("summer", "winter")),
        ),
    )

    private fun request(allowTools: Boolean, turns: List<ToolTurn> = listOf(ToolTurn.User("hi"))) =
        OpenAiToolFormat.buildRequest("m", "sys", turns, listOf(spec), allowTools)

    @Test
    fun `request declares tools with a JSON schema and tool_choice auto`() {
        val req = request(true)
        val tool = req["tools"]!!.jsonArray[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        val fn = tool["function"]!!.jsonObject
        assertEquals("find_items", fn["name"]!!.jsonPrimitive.content)
        val params = fn["parameters"]!!.jsonObject
        assertEquals("object", params["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("query"), params["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val props = params["properties"]!!.jsonObject
        assertEquals("integer", props["limit"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val tags = props["tags"]!!.jsonObject
        assertEquals("array", tags["type"]!!.jsonPrimitive.content)
        assertEquals("string", tags["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("summer", "winter"),
            props["season"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("auto", req["tool_choice"]!!.jsonPrimitive.content)
        assertNull(req["response_format"])
    }

    @Test
    fun `schema omits required when no param is required`() {
        val optional = ToolSpec("t", "d", listOf(ToolParam("a", ToolParamType.STRING, "a")))
        val req = OpenAiToolFormat.buildRequest("m", "s", emptyList(), listOf(optional), true)
        val params = req["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["parameters"]!!.jsonObject
        assertNull(params["required"])
    }

    @Test
    fun `request omits tools and tool_choice when tools are disallowed`() {
        val req = request(false)
        assertNull(req["tools"])
        assertNull(req["tool_choice"])
    }

    @Test
    fun `assistant calls and tool results map to tool_calls and tool messages`() {
        val call = ToolCall("c1", "find_items", buildJsonObject { put("query", "red") })
        val turns = listOf(
            ToolTurn.User("hi"),
            ToolTurn.Assistant(null, listOf(call)),
            ToolTurn.ToolResults(listOf(ToolCallResult("c1", "find_items", buildJsonObject { put("n", 1) }))),
        )
        val msgs = request(true, turns)["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool"), msgs.map { it["role"]!!.jsonPrimitive.content })
        val tc = msgs[2]["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("c1", tc["id"]!!.jsonPrimitive.content)
        assertEquals("""{"query":"red"}""", tc["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        assertEquals("c1", msgs[3]["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("""{"n":1}""", msgs[3]["content"]!!.jsonPrimitive.content)
    }

    private fun toolCallBody(arguments: String) = """
        {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
          {"id":"c1","type":"function","function":{"name":"find_items","arguments":${JsonPrimitive(arguments)}}}]}}]}
    """.trimIndent()

    @Test
    fun `response with tool_calls parses to Calls`() {
        val step = OpenAiToolFormat.parseResponse(toolCallBody("""{"query":"red"}"""), json)
        val calls = (step as ToolStep.Calls).calls
        assertEquals("c1", calls[0].id)
        assertEquals("find_items", calls[0].name)
        assertEquals("red", calls[0].args["query"]!!.jsonPrimitive.content)
    }

    @Test
    fun `response with content parses to Final`() {
        val step = OpenAiToolFormat.parseResponse(
            """{"choices":[{"message":{"role":"assistant","content":"{\"type\":\"text\"}"}}]}""", json,
        )
        assertEquals(ToolStep.Final("""{"type":"text"}"""), step)
    }

    @Test
    fun `malformed arguments become an error object for that call`() {
        val step = OpenAiToolFormat.parseResponse(toolCallBody("{not json"), json) as ToolStep.Calls
        assertTrue(step.calls[0].args.containsKey(com.closet.core.data.ai.tools.ARGS_PARSE_ERROR_KEY))
    }

    @Test
    fun `non-object arguments also carry the parse error marker`() {
        val step = OpenAiToolFormat.parseResponse(toolCallBody("[1,2]"), json) as ToolStep.Calls
        assertTrue(step.calls[0].args.containsKey(com.closet.core.data.ai.tools.ARGS_PARSE_ERROR_KEY))
    }

    @Test
    fun `model_not_found with tools in the model name is not a tool rejection`() {
        val body = """{"error":{"message":"The model `gpt-tools-preview` does not exist or you do not have access to it.","type":"invalid_request_error","param":null,"code":"model_not_found"}}"""
        assertFalse(OpenAiToolFormat.isToolsUnsupported(404, body))
    }

    @Test
    fun `error param tools or tool_choice is a rejection`() {
        assertTrue(OpenAiToolFormat.isToolsUnsupported(400, """{"error":{"message":"Invalid value","param":"tools","code":"x"}}"""))
        assertTrue(OpenAiToolFormat.isToolsUnsupported(400, """{"error":{"message":"Invalid value","param":"tool_choice"}}"""))
    }

    @Test
    fun `a tool word far from the signal is not a rejection`() {
        val body = "Your tools list is fine. " + "x".repeat(200) + " The requested thing is not supported here."
        assertFalse(OpenAiToolFormat.isToolsUnsupported(400, body))
    }

    @Test
    fun `config key is built from the given values`() {
        assertEquals("http://a\u0000m", OpenAiChatProvider.configKey("http://a", "m"))
    }

    @Test
    fun `model_not_found does not cache a tool rejection`() = runTest {
        val base = kotlinx.coroutines.flow.MutableStateFlow("http://local")
        val provider = OpenAiChatProvider(mockk<AiHttpClient>(relaxed = true), prefs(base), json)
        val body = """{"error":{"message":"The model `gpt-tools-preview` does not exist or you do not have access to it.","type":"invalid_request_error","param":null,"code":"model_not_found"}}"""
        val result = provider.handleResponse(404, body, "m", OpenAiChatProvider.configKey("http://local", "m"))
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is ToolsUnsupportedException)
        assertFalse(result.exceptionOrNull()!!.message!!.contains("gpt-tools-preview"))
        assertTrue(provider.supportsTools())
    }

    @Test
    fun `realistic OpenAI error bodies are classified correctly`() {
        val contextLength = """{"error":{"message":"This model's maximum context length is 4096 tokens. However, your messages and tools resulted in 5000 tokens.","type":"invalid_request_error","param":"messages","code":"context_length_exceeded"}}"""
        val unsupported = """{"error":{"message":"tools is not supported for this model","type":"invalid_request_error"}}"""
        assertFalse(OpenAiToolFormat.isToolsUnsupported(400, contextLength))
        assertTrue(OpenAiToolFormat.isToolsUnsupported(400, unsupported))
    }

    @Test
    fun `unsupported detection needs a status and a tools mention`() {
        assertTrue(OpenAiToolFormat.isToolsUnsupported(400, """{"error":"tools not supported"}"""))
        assertTrue(OpenAiToolFormat.isToolsUnsupported(422, "unknown field tool_choice"))
        assertTrue(OpenAiToolFormat.isToolsUnsupported(400, "Tools are Not Supported by this model"))
        assertFalse(OpenAiToolFormat.isToolsUnsupported(400, "context length exceeded, tools count toward the limit"))
        assertFalse(OpenAiToolFormat.isToolsUnsupported(400, "bad model"))
        assertFalse(OpenAiToolFormat.isToolsUnsupported(500, "tools"))
    }

    private fun prefs(base: kotlinx.coroutines.flow.MutableStateFlow<String>) =
        mockk<AiPreferencesRepository> {
            every { getOpenAiBaseUrl() } returns base
            every { getOpenAiModel() } returns kotlinx.coroutines.flow.flowOf("m")
        }

    @Test
    fun `an unsupported error marks only the rejected config`() = runTest {
        val base = kotlinx.coroutines.flow.MutableStateFlow("http://local")
        val provider = OpenAiChatProvider(mockk<AiHttpClient>(relaxed = true), prefs(base), json)
        assertTrue(provider.supportsTools())

        val key = "http://local\u0000m"
        val result = provider.handleResponse(400, "tools is not supported", "m", key)

        assertTrue(result.exceptionOrNull() is ToolsUnsupportedException)
        assertFalse(provider.supportsTools())
        base.value = "https://api.openai.com"
        assertTrue(provider.supportsTools())
        base.value = "http://local"
        assertFalse(provider.supportsTools())
    }

    @Test
    fun `other http errors do not mark the provider`() = runTest {
        val base = kotlinx.coroutines.flow.MutableStateFlow("http://local")
        val provider = OpenAiChatProvider(mockk<AiHttpClient>(relaxed = true), prefs(base), json)
        val result = provider.handleResponse(401, "bad key", "m", "k")
        assertTrue(result.isFailure)
        assertTrue(provider.supportsTools())
    }
}
