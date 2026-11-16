package com.closet.core.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The outcome of one tool call: the compact [json] sent back to the model, and [itemIds], every
 * wardrobe item id that appears in [json]. The chat runner uses [itemIds] to drop ids the model
 * invents in its final answer.
 */
data class ToolResult(val json: JsonElement, val itemIds: Set<Long>)

/** A read-only wardrobe query the chat model can call. Never writes. */
interface WardrobeTool {
    val spec: ToolSpec

    /**
     * Runs the tool. Bad arguments produce an `{"error": ...}` result rather than an exception;
     * only cancellation propagates.
     */
    suspend fun run(args: JsonObject): ToolResult
}
