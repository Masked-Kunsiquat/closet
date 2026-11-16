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

/** Pure request-building and response-parsing for OpenAI-compatible tool calling. */
internal object OpenAiToolFormat {

    fun buildRequest(
        model: String,
        system: String,
        turns: List<ToolTurn>,
        tools: List<ToolSpec>,
        allowTools: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("messages", buildMessages(system, turns))
        if (allowTools && tools.isNotEmpty()) {
            put("tools", JsonArray(tools.map { toolJson(it) }))
            put("tool_choice", "auto")
        }
    }

    private fun buildMessages(system: String, turns: List<ToolTurn>): JsonArray = buildJsonArray {
        add(buildJsonObject { put("role", "system"); put("content", system) })
        for (turn in turns) {
            when (turn) {
                is ToolTurn.User -> add(buildJsonObject { put("role", "user"); put("content", turn.text) })
                is ToolTurn.Assistant -> add(buildJsonObject {
                    put("role", "assistant")
                    if (turn.text != null) put("content", turn.text) else put("content", JsonNull)
                    if (turn.calls.isNotEmpty()) {
                        put("tool_calls", JsonArray(turn.calls.map { call ->
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.name)
                                    put("arguments", call.args.toString())
                                })
                            }
                        }))
                    }
                })
                is ToolTurn.ToolResults -> turn.results.forEach { r ->
                    add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", r.callId)
                        put("content", r.result.toString())
                    })
                }
            }
        }
    }

    fun toolJson(spec: ToolSpec): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", spec.name)
            put("description", spec.description)
            put("parameters", schema(spec.params))
        })
    }

    private fun schema(params: List<ToolParam>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            params.forEach { p -> put(p.name, paramSchema(p)) }
        })
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

    /** Parses a chat-completions body into a step. Throws on a malformed envelope. */
    fun parseResponse(body: String, json: Json): ToolStep {
        val root = json.parseToJsonElement(body.trim()).jsonObject
        val choices = root["choices"]?.jsonArray ?: error("choices missing from OpenAI response")
        check(choices.isNotEmpty()) { "choices array is empty in OpenAI response" }
        val message = choices[0].jsonObject["message"]?.jsonObject
            ?: error("choices[0].message missing from OpenAI response")
        val rawCalls = (message["tool_calls"] as? JsonArray).orEmpty()
        if (rawCalls.isNotEmpty()) {
            val calls = rawCalls.mapIndexed { i, el ->
                val obj = el.jsonObject
                val fn = obj["function"]?.jsonObject ?: error("tool_call function missing")
                ToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_$i",
                    name = fn["name"]?.jsonPrimitive?.contentOrNull ?: error("tool_call name missing"),
                    args = parseArguments(fn["arguments"], json),
                )
            }
            return ToolStep.Calls(calls)
        }
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull
            ?: error("choices[0].message.content missing from OpenAI response")
        return ToolStep.Final(content)
    }

    /** `arguments` is a JSON string; anything unparseable becomes `{ARGS_PARSE_ERROR_KEY: ...}` so the runner skips the tool. */
    private fun parseArguments(raw: kotlinx.serialization.json.JsonElement?, json: Json): JsonObject {
        if (raw is JsonObject) return raw
        val text = (raw as? JsonPrimitive)?.contentOrNull
        if (text.isNullOrBlank()) return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(text) as? JsonObject
                ?: JsonObject(mapOf(ARGS_PARSE_ERROR_KEY to JsonPrimitive("arguments were not a JSON object")))
        } catch (e: kotlinx.serialization.SerializationException) {
            JsonObject(mapOf(ARGS_PARSE_ERROR_KEY to JsonPrimitive("arguments were not valid JSON")))
        }
    }

    /** Whether an HTTP error means the server doesn't accept tool calling. */
    fun isToolsUnsupported(status: Int, body: String): Boolean {
        if (status !in UNSUPPORTED_STATUSES) return false
        val root = try {
            Json.parseToJsonElement(body.trim()) as? JsonObject
        } catch (e: Exception) {
            null
        }
        val errorElement = root?.get("error")
        val error = errorElement as? JsonObject
        val param = (error?.get("param") as? JsonPrimitive)?.contentOrNull
        if (param == "tools" || param == "tool_choice") return true
        val code = (error?.get("code") as? JsonPrimitive)?.contentOrNull
        if (code != null && code in NON_TOOL_CODES) return false
        // Judge the human-readable message when the body has one, else the raw body.
        val text = ((error?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: (errorElement as? JsonPrimitive)?.contentOrNull
            ?: body).lowercase()
        return TOOL_WORDS.findAll(text).any { tool ->
            val from = maxOf(0, tool.range.first - WINDOW)
            val to = minOf(text.length, tool.range.last + 1 + WINDOW)
            val near = text.substring(from, to)
            SIGNALS.any { near.contains(it) }
        }
    }

    private val TOOL_WORDS = Regex("tool_choice|tools|tool calling|function calling")
    private const val WINDOW = 60

    /** Error codes that are never about tool support, even if the message mentions a tool-ish word. */
    private val NON_TOOL_CODES = setOf(
        "model_not_found", "context_length_exceeded", "invalid_api_key", "insufficient_quota",
        "rate_limit_exceeded",
    )

    private val SIGNALS = listOf(
        "not support", "unsupported", "unknown parameter", "unknown field", "unrecognized",
        "not allowed", "extra fields not permitted", "invalid tool",
    )

    private val UNSUPPORTED_STATUSES = setOf(400, 404, 422)
}
