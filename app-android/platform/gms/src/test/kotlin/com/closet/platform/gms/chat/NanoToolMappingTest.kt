package com.closet.platform.gms.chat

import com.closet.core.data.ai.tools.ToolCall
import com.closet.core.data.ai.tools.ToolCallResult
import com.closet.core.data.ai.tools.ToolParam
import com.closet.core.data.ai.tools.ToolParamType
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.common.FunctionCall
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.FunctionCallPart
import com.google.mlkit.genai.prompt.FunctionResponsePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.schema.tools.FunctionType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoToolMappingTest {

    private val spec = ToolSpec(
        name = "search_items",
        description = "Find wardrobe items",
        params = listOf(
            ToolParam("category", ToolParamType.STRING, "Category name", required = true, enumValues = listOf("Tops", "Shoes")),
            ToolParam("limit", ToolParamType.INTEGER, "Max results"),
            ToolParam("include_archived", ToolParamType.BOOLEAN, "Include archived items"),
            ToolParam("colors", ToolParamType.STRING_ARRAY, "Color names"),
        ),
    )

    // ── ToolSpec → FunctionDeclaration ───────────────────────────────────────

    @Test
    fun `spec maps to an object declaration with typed properties`() {
        val declaration = spec.toFunctionDeclaration()
        assertEquals("search_items", declaration.name)
        assertEquals("Find wardrobe items", declaration.description)
        val schema = declaration.parameters!!
        assertEquals(FunctionType.OBJECT, schema.type)
        val props = schema.properties!!
        assertEquals(listOf("category", "limit", "include_archived", "colors"), props.keys.toList())
        assertEquals(FunctionType.STRING, props.getValue("category").type)
        assertEquals("Category name", props.getValue("category").description)
        assertEquals(FunctionType.INTEGER, props.getValue("limit").type)
        assertEquals(FunctionType.BOOLEAN, props.getValue("include_archived").type)
    }

    @Test
    fun `string array param maps to an array of strings`() {
        val colors = spec.toFunctionDeclaration().parameters!!.properties!!.getValue("colors")
        assertEquals(FunctionType.ARRAY, colors.type)
        assertEquals(FunctionType.STRING, colors.items!!.type)
    }

    @Test
    fun `required list holds only required params`() {
        assertEquals(listOf("category"), spec.toFunctionDeclaration().parameters!!.required)
    }

    @Test
    fun `enum values pass through and are absent otherwise`() {
        val props = spec.toFunctionDeclaration().parameters!!.properties!!
        assertEquals(listOf("Tops", "Shoes"), props.getValue("category").enum)
        assertNull(props.getValue("limit").enum)
    }

    // ── FunctionCall → ToolCall ──────────────────────────────────────────────

    @Test
    fun `each FunctionArgValue subtype maps to its JSON value`() {
        val call = FunctionCall(
            "search_items",
            mapOf(
                "s" to FunctionArgValue.StringValue("Tops"),
                "i" to FunctionArgValue.IntValue(5),
                "l" to FunctionArgValue.LongValue(9_000_000_000L),
                "d" to FunctionArgValue.DoubleValue(1.5),
                "b" to FunctionArgValue.BoolValue(true),
                "list" to FunctionArgValue.ListValue(
                    listOf(FunctionArgValue.StringValue("red"), FunctionArgValue.IntValue(2)),
                ),
                "map" to FunctionArgValue.MapValue(mapOf("k" to FunctionArgValue.BoolValue(false))),
            ),
            "call-1",
        )
        val expected = buildJsonObject {
            put("s", "Tops")
            put("i", 5)
            put("l", 9_000_000_000L)
            put("d", 1.5)
            put("b", true)
            putJsonArray("list") {
                add(JsonPrimitive("red"))
                add(JsonPrimitive(2))
            }
            putJsonObject("map") { put("k", false) }
        }
        val toolCall = call.toToolCall(fallbackId = "unused")
        assertEquals(ToolCall("call-1", "search_items", expected), toolCall)
    }

    @Test
    fun `blank call id gets the fallback id`() {
        val call = FunctionCall("wardrobe_summary", emptyMap(), "")
        assertEquals("call_1_3", call.toToolCall(fallbackId = "call_1_3").id)
    }

    @Test
    fun `generated ids are unique across rounds of one turn`() {
        val calls = listOf(FunctionCall("a", emptyMap(), ""), FunctionCall("b", emptyMap(), ""))
        val first = candidateStep(null, calls, round = toolRound(listOf(ToolTurn.User("q")))) as ToolStep.Calls
        val turns = listOf(
            ToolTurn.User("q"),
            ToolTurn.Assistant(null, first.calls),
            ToolTurn.ToolResults(emptyList()),
        )
        val second = candidateStep(null, calls, round = toolRound(turns)) as ToolStep.Calls
        val ids = (first.calls + second.calls).map { it.id }
        assertEquals(listOf("call_0_0", "call_0_1", "call_1_0", "call_1_1"), ids)
    }

    @Test
    fun `tool round counts only this turn's assistant calls`() {
        val turns = listOf(
            ToolTurn.User("old"),
            ToolTurn.Assistant("old answer"),
            ToolTurn.User("q"),
            ToolTurn.Assistant(null, listOf(ToolCall("x", "a", buildJsonObject { }))),
            ToolTurn.ToolResults(emptyList()),
        )
        assertEquals(1, toolRound(turns))
        assertEquals(0, toolRound(turns.take(3)))
    }

    // ── in-turn trimming ─────────────────────────────────────────────────────

    private val user = ToolTurn.User("q")
    private fun pair(n: Int): List<ToolTurn> = listOf(
        ToolTurn.Assistant(null, listOf(ToolCall("c$n", "t", buildJsonObject { }))),
        ToolTurn.ToolResults(listOf(ToolCallResult("c$n", "t", buildJsonObject { }))),
    )

    @Test
    fun `over budget drops the oldest call and result pairs first`() {
        val tail = listOf(user) + pair(1) + pair(2) + pair(3)
        // Fits once at most one pair is left.
        val kept = trimToolRounds(tail) { it.size <= 3 }
        assertEquals(listOf(user) + pair(3), kept)
    }

    @Test
    fun `trimming never splits a pair`() {
        val tail = listOf(user) + pair(1) + pair(2) + pair(3)
        val seen = mutableListOf<List<ToolTurn>>()
        trimToolRounds(tail) { seen += it; it.size <= 5 }
        // Every candidate is the user turn plus whole pairs.
        seen.forEach { candidate ->
            assertEquals(user, candidate.first())
            assertEquals(1, candidate.size % 2)
            candidate.drop(1).chunked(2).forEach { (a, r) ->
                assertTrue(a is ToolTurn.Assistant)
                assertTrue(r is ToolTurn.ToolResults)
            }
        }
        assertEquals(listOf(user) + pair(2) + pair(3), trimToolRounds(tail) { it.size <= 5 })
    }

    @Test
    fun `user turn and latest pair are kept even when still over budget`() {
        val tail = listOf(user) + pair(1) + pair(2)
        assertEquals(listOf(user) + pair(2), trimToolRounds(tail) { false })
        assertEquals(listOf(user), trimToolRounds(listOf(user)) { false })
    }

    @Test
    fun `under budget leaves the turn unchanged`() {
        val tail = listOf(user) + pair(1) + pair(2)
        assertEquals(tail, trimToolRounds(tail) { true })
    }

    // ── candidate → ToolStep ─────────────────────────────────────────────────

    @Test
    fun `candidate with function calls becomes Calls`() {
        val calls = listOf(FunctionCall("wardrobe_summary", emptyMap(), "a"))
        val step = candidateStep(text = "ignored", functionCalls = calls)
        assertEquals(ToolStep.Calls(listOf(ToolCall("a", "wardrobe_summary", buildJsonObject { }))), step)
    }

    @Test
    fun `candidate with only text becomes Final`() {
        assertEquals(ToolStep.Final("{\"type\":\"text\",\"text\":\"hi\"}"), candidateStep("{\"type\":\"text\",\"text\":\"hi\"}", emptyList()))
    }

    @Test
    fun `candidate with neither text nor calls is null`() {
        assertNull(candidateStep(null, emptyList()))
        assertNull(candidateStep("  ", emptyList()))
    }

    // ── ToolTurn → Content ───────────────────────────────────────────────────

    @Test
    fun `turns map to roles and part types`() {
        val args = buildJsonObject { put("category", "Tops"); put("limit", 3) }
        val turns = listOf(
            ToolTurn.User("earlier question"),
            ToolTurn.Assistant("earlier answer"),
            ToolTurn.User("what tops do I have?"),
            ToolTurn.Assistant(null, listOf(ToolCall("c1", "search_items", args))),
            ToolTurn.ToolResults(listOf(ToolCallResult("c1", "search_items", buildJsonObject { put("count", 2) }))),
        )
        val contents = turns.toContents()

        assertEquals(
            listOf(Content.Role.USER, Content.Role.MODEL, Content.Role.USER, Content.Role.MODEL, Content.Role.USER),
            contents.map { it.role },
        )
        assertEquals("earlier question", (contents[0].parts.single() as TextPart).textString)
        assertEquals("earlier answer", (contents[1].parts.single() as TextPart).textString)

        val callPart = contents[3].parts.single() as FunctionCallPart
        assertEquals("search_items", callPart.functionCall.name)
        assertEquals("c1", callPart.functionCall.id)
        // The call's args round-trip back to the same JSON.
        assertEquals(args, callPart.functionCall.toToolCall("unused").args)

        val responsePart = contents[4].parts.single() as FunctionResponsePart
        assertEquals("c1", responsePart.id)
        assertEquals("search_items", responsePart.name)
        assertEquals(mapOf("count" to 2L), responsePart.response)
    }

    @Test
    fun `assistant turn with text and calls keeps both parts`() {
        val turn = ToolTurn.Assistant("let me look", listOf(ToolCall("c1", "wardrobe_summary", buildJsonObject { })))
        val parts = listOf(turn).toContents().single().parts
        assertTrue(parts[0] is TextPart)
        assertTrue(parts[1] is FunctionCallPart)
    }

    @Test
    fun `inline system prompt is prepended to the first user turn only`() {
        val contents = listOf(ToolTurn.User("one"), ToolTurn.Assistant("two"), ToolTurn.User("three"))
            .toContents(inlineSystemPrompt = "SYS")
        assertEquals("SYS\n\none", (contents[0].parts.single() as TextPart).textString)
        assertEquals("three", (contents[2].parts.single() as TextPart).textString)
    }

    // ── tools-off flat history ───────────────────────────────────────────────

    private val flatTurns = listOf(
        ToolTurn.User("what tops do I have"),
        ToolTurn.Assistant("checking", listOf(ToolCall("c1", "search_items", buildJsonObject { put("category", "Tops") }))),
        ToolTurn.ToolResults(listOf(ToolCallResult("c1", "search_items", buildJsonObject { put("count", 2) }))),
    )

    @Test
    fun `flat contents have only text parts with the flattened strings and roles`() {
        val contents = flatTurns.toFlatContents()

        assertEquals(listOf(Content.Role.USER, Content.Role.MODEL, Content.Role.USER), contents.map { it.role })
        assertTrue(contents.all { c -> c.parts.all { it is TextPart } })
        assertEquals("what tops do I have", (contents[0].parts.single() as TextPart).textString)
        assertEquals(
            listOf("checking", """Called search_items with {"category":"Tops"}"""),
            contents[1].parts.map { (it as TextPart).textString },
        )
        assertEquals("""Result of search_items: {"count":2}""", (contents[2].parts.single() as TextPart).textString)
    }

    @Test
    fun `flat contents prepend the inline system prompt to the first user turn`() {
        val contents = flatTurns.toFlatContents(inlineSystemPrompt = "SYS")
        assertEquals("SYS\n\nwhat tops do I have", (contents[0].parts.single() as TextPart).textString)
    }

    @Test
    fun `tools-on mapping still emits function parts`() {
        val contents = flatTurns.toContents()
        assertTrue(contents[1].parts.any { it is FunctionCallPart })
        assertTrue(contents[2].parts.single() is FunctionResponsePart)
    }

    // ── tool result JSON → response map ──────────────────────────────────────

    @Test
    fun `object result converts to plain Kotlin values and drops nulls`() {
        val json = buildJsonObject {
            put("name", "Blue shirt")
            put("count", 3)
            put("ratio", 0.5)
            put("archived", false)
            put("note", JsonNull)
            putJsonArray("ids") { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }
            putJsonObject("nested") { put("k", "v") }
        }
        assertEquals(
            mapOf(
                "name" to "Blue shirt",
                "count" to 3L,
                "ratio" to 0.5,
                "archived" to false,
                "ids" to listOf(1L, 2L),
                "nested" to mapOf("k" to "v"),
            ),
            json.toResponseMap(),
        )
    }

    @Test
    fun `non-object result is wrapped under result`() {
        val array: JsonArray = buildJsonArray { add(JsonPrimitive("a")) }
        assertEquals(mapOf("result" to listOf("a")), array.toResponseMap())
        assertEquals(mapOf("result" to 7L), JsonPrimitive(7).toResponseMap())
        assertEquals(emptyMap<String, Any>(), JsonNull.toResponseMap())
    }
}
