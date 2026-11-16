package com.closet.platform.gms.chat

import com.closet.core.data.ai.tools.ToolCall
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
import com.google.mlkit.genai.schema.tools.FunctionDeclaration
import com.google.mlkit.genai.schema.tools.FunctionSchema
import com.google.mlkit.genai.schema.tools.FunctionType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Pure mappings between the provider-neutral tool types (core/data `ai/tools`) and the ML Kit
// GenAI Prompt API's function-calling types. Kept free of `GenerativeModel` so they're host-testable.

/** Maps this spec to an ML Kit declaration: an OBJECT schema with one property per param. */
internal fun ToolSpec.toFunctionDeclaration(): FunctionDeclaration = FunctionDeclaration(
    name,
    description,
    FunctionSchema(
        FunctionType.OBJECT,
        null,
        params.associate { it.name to it.toSchema() },
        params.filter { it.required }.map { it.name },
    ),
)

private fun ToolParam.toSchema(): FunctionSchema = when (type) {
    ToolParamType.STRING -> FunctionSchema(FunctionType.STRING, description, null, null, enumValues)
    ToolParamType.INTEGER -> FunctionSchema(FunctionType.INTEGER, description, null, null, enumValues)
    ToolParamType.BOOLEAN -> FunctionSchema(FunctionType.BOOLEAN, description, null, null, enumValues)
    // Positional overload up to `items` (8th): type, description, properties, required, enum,
    // format, nullable, items. Enum values constrain the array's elements.
    ToolParamType.STRING_ARRAY -> FunctionSchema(
        FunctionType.ARRAY,
        description,
        null,
        null,
        null,
        null,
        null,
        FunctionSchema(FunctionType.STRING, null, null, null, enumValues),
    )
}

/**
 * Maps a model function call to a [ToolCall]. A blank id (the model may omit one) becomes
 * `call_<index>`, so the matching `FunctionResponsePart` can still reference it.
 */
internal fun FunctionCall.toToolCall(index: Int): ToolCall = ToolCall(
    id = id?.takeIf { it.isNotBlank() } ?: "call_$index",
    name = name,
    args = JsonObject(arguments.mapValues { (_, value) -> value.toJson() }),
)

private fun FunctionArgValue.toJson(): JsonElement = when (this) {
    is FunctionArgValue.StringValue -> JsonPrimitive(value)
    is FunctionArgValue.IntValue -> JsonPrimitive(value)
    is FunctionArgValue.LongValue -> JsonPrimitive(value)
    is FunctionArgValue.DoubleValue -> JsonPrimitive(value)
    is FunctionArgValue.BoolValue -> JsonPrimitive(value)
    is FunctionArgValue.ListValue -> JsonArray(values.map { it.toJson() })
    is FunctionArgValue.MapValue -> JsonObject(map.mapValues { (_, v) -> v.toJson() })
}

/** Reverse of [toJson], for replaying a [ToolCall] as a [FunctionCallPart]. Nulls are dropped. */
private fun JsonElement.toArgValue(): FunctionArgValue? = when (this) {
    is JsonNull -> null
    is JsonObject -> FunctionArgValue.MapValue(toArgMap())
    is JsonArray -> FunctionArgValue.ListValue(mapNotNull { it.toArgValue() })
    is JsonPrimitive -> when {
        isString -> FunctionArgValue.StringValue(content)
        booleanOrNull != null -> FunctionArgValue.BoolValue(booleanOrNull!!)
        longOrNull != null -> longOrNull!!.let {
            if (it in Int.MIN_VALUE..Int.MAX_VALUE) FunctionArgValue.IntValue(it.toInt()) else FunctionArgValue.LongValue(it)
        }
        doubleOrNull != null -> FunctionArgValue.DoubleValue(doubleOrNull!!)
        else -> FunctionArgValue.StringValue(content)
    }
}

private fun JsonObject.toArgMap(): Map<String, FunctionArgValue> =
    mapNotNull { (key, value) -> value.toArgValue()?.let { key to it } }.toMap()

/**
 * The step a candidate represents: [ToolStep.Calls] when it has function calls, [ToolStep.Final]
 * when it has non-blank text, and null when it has neither.
 */
internal fun candidateStep(text: String?, functionCalls: List<FunctionCall>): ToolStep? = when {
    functionCalls.isNotEmpty() -> ToolStep.Calls(functionCalls.mapIndexed { i, call -> call.toToolCall(i) })
    !text.isNullOrBlank() -> ToolStep.Final(text)
    else -> null
}

/**
 * Maps the tool conversation to ML Kit contents. [ToolTurn.User] → USER text; [ToolTurn.Assistant]
 * → MODEL with an optional text part and one [FunctionCallPart] per call; [ToolTurn.ToolResults]
 * → USER with one [FunctionResponsePart] per result (ML Kit has only USER and MODEL roles).
 *
 * When [inlineSystemPrompt] is non-null (no system-instruction support), it is prepended to the
 * first user text turn.
 */
internal fun List<ToolTurn>.toContents(inlineSystemPrompt: String? = null): List<Content> {
    val firstUser = indexOfFirst { it is ToolTurn.User }
    return mapIndexed { i, turn ->
        when (turn) {
            is ToolTurn.User -> {
                val text = if (i == firstUser && inlineSystemPrompt != null) "$inlineSystemPrompt\n\n${turn.text}" else turn.text
                Content.Builder().role(Content.Role.USER).addPart(TextPart(text)).build()
            }
            is ToolTurn.Assistant -> Content.Builder().role(Content.Role.MODEL).apply {
                turn.text?.takeIf { it.isNotBlank() }?.let { addPart(TextPart(it)) }
                turn.calls.forEach { call ->
                    addPart(FunctionCallPart(FunctionCall(call.name, call.args.toArgMap(), call.id)))
                }
            }.build()
            is ToolTurn.ToolResults -> Content.Builder().role(Content.Role.USER).apply {
                turn.results.forEach { addPart(FunctionResponsePart(it.callId, it.name, it.result.toResponseMap())) }
            }.build()
        }
    }
}

/**
 * Converts a tool result to the plain-value map `FunctionResponsePart` takes: objects → `Map`,
 * arrays → `List`, primitives → `String`/`Long`/`Double`/`Boolean`, nulls dropped. A non-object
 * result is wrapped as `{"result": …}` (and a null result is an empty map).
 */
internal fun JsonElement.toResponseMap(): Map<String, Any> = when (this) {
    is JsonObject -> toPlainMap()
    else -> toPlain()?.let { mapOf("result" to it) } ?: emptyMap()
}

private fun JsonObject.toPlainMap(): Map<String, Any> =
    mapNotNull { (key, value) -> value.toPlain()?.let { key to it } }.toMap()

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> toPlainMap()
    is JsonArray -> mapNotNull { it.toPlain() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull ?: content
    }
}
