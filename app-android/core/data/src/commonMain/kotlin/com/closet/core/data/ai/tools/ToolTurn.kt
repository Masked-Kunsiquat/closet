package com.closet.core.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A tool invocation requested by the model. */
data class ToolCall(
    val id: String,
    val name: String,
    val args: JsonObject,
    /** Provider-specific data to echo back with this call (e.g. Gemini `thoughtSignature`). */
    val providerData: JsonObject? = null,
)

/** The result of one [ToolCall], sent back to the model. */
data class ToolCallResult(val callId: String, val name: String, val result: JsonElement)

/** One entry in a tool-calling conversation. */
sealed interface ToolTurn {
    data class User(val text: String) : ToolTurn

    data class Assistant(val text: String?, val calls: List<ToolCall> = emptyList()) : ToolTurn

    data class ToolResults(val results: List<ToolCallResult>) : ToolTurn
}

/** What the model did on one step: asked for tools, or gave its final reply. */
sealed interface ToolStep {
    data class Calls(val calls: List<ToolCall>) : ToolStep

    /** [text] is the raw JSON reply, parsed by `ChatResponseParser`. */
    data class Final(val text: String) : ToolStep
}
