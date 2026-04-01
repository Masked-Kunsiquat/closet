package com.closet.core.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses the structured JSON response from any [ChatAiProvider] implementation into
 * a [ChatResponse].
 *
 * Expected shape:
 * ```json
 * { "type": "text"|"items"|"outfit", "text": "...", "item_ids": [...], "reason": "..." }
 * ```
 *
 * Unknown `type` values fall back to [ChatResponse.Text] so new response types added
 * server-side degrade gracefully rather than crashing.
 */
object ChatResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [responseText] into a [ChatResponse].
     *
     * Returns [Result.failure] if the text is not valid JSON, missing the required `type`
     * or `text` fields, or structurally malformed. Providers wrap this in [runCatching].
     */
    fun parse(responseText: String): Result<ChatResponse> = runCatching {
        val obj = json.parseToJsonElement(responseText.trim()).jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: error("missing 'type' field in chat response")
        val text = obj["text"]?.jsonPrimitive?.content ?: error("missing 'text' field in chat response")

        when (type) {
            "items" -> {
                val idsElement = obj["item_ids"]
                    ?: throw IllegalArgumentException("'item_ids' missing in chat response (type='items')")
                val ids = idsElement.jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }
                if (ids.isEmpty()) throw IllegalArgumentException(
                    "'item_ids' resolved to empty list in chat response (type='items', raw=$idsElement)"
                )
                ChatResponse.WithItems(text, ids)
            }
            "outfit" -> {
                val idsElement = obj["item_ids"]
                    ?: throw IllegalArgumentException("'item_ids' missing in chat response (type='outfit')")
                val ids = idsElement.jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }
                if (ids.isEmpty()) throw IllegalArgumentException(
                    "'item_ids' resolved to empty list in chat response (type='outfit', raw=$idsElement)"
                )
                val reason = obj["reason"]?.jsonPrimitive?.content.orEmpty()
                ChatResponse.WithOutfit(text, ids, reason)
            }
            else -> ChatResponse.Text(text)   // "text" + unknown types
        }
    }
}
