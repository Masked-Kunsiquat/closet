package com.closet.platform.gms.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Structured-output schema for Nano chat replies. The ML Kit schema compiler (KSP) generates
 * its `GenerableProvider`.
 *
 * Mirrors the shapes [ChatResponseParser] accepts (see `ChatPromptPrefix.SYSTEM_PROMPT`). The
 * optional `action` object is left out of the schema; the parser treats a missing action as
 * "no action".
 */
@Generable(description = "A reply from a personal wardrobe assistant")
data class NanoChatReply(
    @Guide(
        description = "text for a plain answer, items when the answer references specific " +
            "wardrobe items, outfit for an outfit suggestion",
        enumValues = ["text", "items", "outfit"],
    )
    val type: String,
    @Guide(description = "A concise, conversational answer of 1 to 3 sentences")
    val text: String,
    @Guide(
        description = "Numeric IDs of wardrobe items from the context. Empty for text; the " +
            "relevant items for items; 2 to 4 items forming a complete outfit for outfit",
    )
    val itemIds: List<Long>,
    @Guide(description = "One-sentence rationale. Required for outfit, otherwise null")
    val reason: String?,
)

/** Serializes this reply to the JSON shape [ChatResponseParser] expects. */
internal fun NanoChatReply.toParserJson(): String = buildJsonObject {
    put("type", type)
    put("text", text)
    putJsonArray("item_ids") { itemIds.forEach { add(JsonPrimitive(it)) } }
    if (reason != null) put("reason", reason)
}.toString()

/**
 * Converts this reply via [ChatResponseParser], so typed and plain-text replies share one set
 * of validation rules and fallbacks.
 */
internal fun NanoChatReply.toChatResponse(): Result<ChatResponse> =
    ChatResponseParser.parse(toParserJson())
