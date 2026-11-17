package com.closet.features.chat.ai

import com.closet.core.data.ai.tools.ARGS_PARSE_ERROR_KEY
import com.closet.core.data.ai.tools.ToolCall
import com.closet.core.data.ai.tools.ToolParam
import com.closet.core.data.ai.tools.ToolParamType
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Pure request-building and response-parsing for Gemini generateContent function calling. */
internal object GeminiToolFormat {

    private const val THOUGHT_SIGNATURE = "thoughtSignature"

    /** `providerData` key set (to the id) when the id came from the server rather than synthesized. */
    private const val SERVER_ID = "serverId"

    /**
     * With [allowTools] false the declarations are omitted and `responseMimeType` is JSON (Gemini
     * rejects JSON mime together with function calling). With tools on, no mime type is sent.
     */

    fun buildRequest(
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): JsonObject = buildJsonObject {
        put("system_instruction", buildJsonObject {
            put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
        })
        put("contents", if (allowTools) buildContents(turns) else buildFlatContents(turns))
        if (allowTools && tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", JsonArray(tools.map { declaration(it) }))
                })
            })
        } else {
            put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
        }
    }

    private fun buildContents(turns: List<ToolTurn>): JsonArray = buildJsonArray {
        val serverIds = turns.filterIsInstance<ToolTurn.Assistant>()
            .flatMap { it.calls }
            .filter { it.providerData?.get(SERVER_ID) != null }
            .mapTo(mutableSetOf()) { it.id }
        for (turn in turns) {
            when (turn) {
                is ToolTurn.User -> add(content("user", listOf(textPart(turn.text))))
                is ToolTurn.Assistant -> {
                    val parts = buildList {
                        turn.text?.takeIf { it.isNotBlank() }?.let { add(textPart(it)) }
                        turn.calls.forEach { c ->
                            add(buildJsonObject {
                                put("functionCall", buildJsonObject {
                                    if (c.id in serverIds) put("id", c.id)
                                    put("name", c.name)
                                    put("args", c.args)
                                })
                                c.providerData?.get(THOUGHT_SIGNATURE)?.let { put(THOUGHT_SIGNATURE, it) }
                            })
                        }
                    }
                    if (parts.isNotEmpty()) add(content("model", parts))
                }
                is ToolTurn.ToolResults -> add(content("user", turn.results.map { r ->
                    buildJsonObject {
                        put("functionResponse", buildJsonObject {
                            if (r.callId in serverIds) put("id", r.callId)
                            put("name", r.name)
                            put("response", asObject(r.result))
                        })
                    }
                }))
            }
        }
    }

    /**
     * Tools-off history: Gemini 400s on function parts without declarations, so calls and results
     * become plain text parts.
     */
    private fun buildFlatContents(turns: List<ToolTurn>): JsonArray = buildJsonArray {
        for (turn in turns) {
            when (turn) {
                is ToolTurn.User -> add(content("user", listOf(textPart(turn.text))))
                is ToolTurn.Assistant -> {
                    val parts = buildList {
                        turn.text?.takeIf { it.isNotBlank() }?.let { add(textPart(it)) }
                        turn.calls.forEach { c -> add(textPart("Called ${c.name} with ${c.args}")) }
                    }
                    if (parts.isNotEmpty()) add(content("model", parts))
                }
                is ToolTurn.ToolResults -> add(content("user", turn.results.map { r ->
                    textPart("Result of ${r.name}: ${r.result}")
                }))
            }
        }
    }

    private fun textPart(text: String) = buildJsonObject { put("text", text) }

    private fun content(role: String, parts: List<JsonObject>) = buildJsonObject {
        put("role", role)
        put("parts", JsonArray(parts))
    }

    /** Gemini requires `response` to be an object; wrap anything else. */
    private fun asObject(result: JsonElement): JsonObject =
        result as? JsonObject ?: buildJsonObject { put("result", result) }

    fun declaration(spec: ToolSpec): JsonObject = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        // Gemini rejects an OBJECT schema with empty `properties`, so no-arg tools omit parameters.
        if (spec.params.isNotEmpty()) put("parameters", schema(spec.params))
    }

    private fun schema(params: List<ToolParam>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { params.forEach { p -> put(p.name, paramSchema(p)) } })
        val required = params.filter { it.required }
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it.name) }))
    }

    private fun paramSchema(p: ToolParam): JsonObject = buildJsonObject {
        when (p.type) {
            ToolParamType.STRING -> put("type", "string")
            ToolParamType.INTEGER -> put("type", "integer")
            ToolParamType.BOOLEAN -> put("type", "boolean")
            ToolParamType.STRING_ARRAY -> {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
            }
        }
        put("description", p.description)
        p.enumValues?.let { values -> put("enum", JsonArray(values.map { JsonPrimitive(it) })) }
    }

    /** Count of prior model turns that made calls; the [parseResponse] round. */
    fun roundOf(turns: List<ToolTurn>): Int = turns.count { it is ToolTurn.Assistant && it.calls.isNotEmpty() }

    /**
     * A call keeps the server's `functionCall.id` when it sends one (echoed back on history);
     * otherwise the id is synthetic, `name#round.index`: unique within a turn and across rounds.
     * Throws on a bad envelope.
     */
    fun parseResponse(body: String, json: Json, round: Int): ToolStep {
        val root = json.parseToJsonElement(body.trim()).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: error("candidates missing from Gemini response")
        check(candidates.isNotEmpty()) { "candidates array is empty in Gemini response" }
        val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
            ?: error("candidates[0].content.parts missing from Gemini response")
        val calls = parts.map { it.jsonObject }.filter { it["functionCall"] is JsonObject }
            .mapIndexed { i, part ->
                val fc = part["functionCall"]!!.jsonObject
                val signature = part[THOUGHT_SIGNATURE]
                val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: error("functionCall name missing")
                val serverId = (fc["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val data = buildMap<String, JsonElement> {
                    signature?.let { put(THOUGHT_SIGNATURE, it) }
                    serverId?.let { put(SERVER_ID, JsonPrimitive(it)) }
                }
                ToolCall(
                    id = serverId ?: "$name#$round.$i",
                    name = name,
                    args = objectArgs(fc["args"]),
                    providerData = data.takeIf { it.isNotEmpty() }?.let { JsonObject(it) },
                )
            }
        if (calls.isNotEmpty()) return ToolStep.Calls(calls)
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        check(text.isNotEmpty()) { "no text or functionCall in Gemini response" }
        return ToolStep.Final(text)
    }

    /** Absent or null args means no arguments; any other non-object value is a parse error the runner reports. */
    private fun objectArgs(args: JsonElement?): JsonObject = when (args) {
        null, is JsonNull -> JsonObject(emptyMap())
        is JsonObject -> args
        else -> JsonObject(mapOf(ARGS_PARSE_ERROR_KEY to JsonPrimitive("args was not a JSON object")))
    }
}
