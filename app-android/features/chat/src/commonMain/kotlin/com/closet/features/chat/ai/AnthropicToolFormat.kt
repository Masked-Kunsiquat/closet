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

/** Pure request-building and response-parsing for Anthropic Messages API tool use. */
internal object AnthropicToolFormat {

    /**
     * With [allowTools] false the tool declarations are still sent (Anthropic rejects history that
     * contains tool_use/tool_result blocks without `tools`) but `tool_choice` is `none`.
     */
    fun buildRequest(
        model: String,
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
        maxTokens: Int,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("system", system)
        put("messages", buildMessages(turns))
        if (tools.isNotEmpty()) {
            put("tools", JsonArray(tools.map { toolJson(it) }))
            if (!allowTools) put("tool_choice", buildJsonObject { put("type", "none") })
        }
    }

    private fun buildMessages(turns: List<ToolTurn>): JsonArray = buildJsonArray {
        for (turn in turns) {
            when (turn) {
                is ToolTurn.User -> add(buildJsonObject { put("role", "user"); put("content", turn.text) })
                is ToolTurn.Assistant -> {
                    if (turn.calls.isEmpty()) {
                        if (!turn.text.isNullOrBlank()) {
                            add(buildJsonObject { put("role", "assistant"); put("content", turn.text) })
                        }
                    } else {
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray {
                                if (!turn.text.isNullOrBlank()) {
                                    add(buildJsonObject { put("type", "text"); put("text", turn.text) })
                                }
                                turn.calls.forEach { c ->
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", c.id)
                                        put("name", c.name)
                                        put("input", c.args)
                                    })
                                }
                            })
                        })
                    }
                }
                is ToolTurn.ToolResults -> add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        turn.results.forEach { r ->
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", r.callId)
                                put("content", r.result.toString())
                            })
                        }
                    })
                })
            }
        }
    }

    fun toolJson(spec: ToolSpec): JsonObject = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        put("input_schema", schema(spec.params))
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

    /** Any `tool_use` block wins; otherwise the text blocks are concatenated. Throws on a bad envelope. */
    fun parseResponse(body: String, json: Json): ToolStep {
        val root = json.parseToJsonElement(body.trim()).jsonObject
        val content = root["content"]?.jsonArray ?: error("content missing from Anthropic response")
        val blocks = content.map { it.jsonObject }
        val calls = blocks.filter { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            .mapIndexed { i, b ->
                ToolCall(
                    id = b["id"]?.jsonPrimitive?.contentOrNull ?: "toolu_$i",
                    name = b["name"]?.jsonPrimitive?.contentOrNull ?: error("tool_use name missing"),
                    args = objectArgs(b["input"]),
                )
            }
        if (calls.isNotEmpty()) return ToolStep.Calls(calls)
        val text = blocks.filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        check(text.isNotEmpty()) { "no text or tool_use in Anthropic response" }
        return ToolStep.Final(text)
    }

    /** Absent or null input means no arguments; any other non-object value is a parse error the runner reports. */
    private fun objectArgs(input: JsonElement?): JsonObject = when (input) {
        null, is JsonNull -> JsonObject(emptyMap())
        is JsonObject -> input
        else -> JsonObject(mapOf(ARGS_PARSE_ERROR_KEY to JsonPrimitive("input was not a JSON object")))
    }
}
