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
        val obj = json.parseToJsonElement(extractJson(responseText)).jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: error("missing 'type' field in chat response")
        val text = obj["text"]?.jsonPrimitive?.content ?: error("missing 'text' field in chat response")

        when (type) {
            "items" -> {
                val idsElement = obj["item_ids"]
                    ?: throw IllegalArgumentException("'item_ids' missing in chat response (type='items')")
                val ids = idsElement.jsonArray.map {
                    it.jsonPrimitive.longOrNull
                        ?: throw IllegalArgumentException("Non-long entry in 'item_ids' for type='items': $it")
                }
                if (ids.isEmpty()) throw IllegalArgumentException(
                    "'item_ids' is empty in chat response (type='items')"
                )
                ChatResponse.WithItems(text, ids)
            }
            "outfit" -> {
                val idsElement = obj["item_ids"]
                    ?: throw IllegalArgumentException("'item_ids' missing in chat response (type='outfit')")
                val ids = idsElement.jsonArray.map {
                    it.jsonPrimitive.longOrNull
                        ?: throw IllegalArgumentException("Non-long entry in 'item_ids' for type='outfit': $it")
                }
                if (ids.size !in 2..4) throw IllegalArgumentException(
                    "outfit 'item_ids' must have 2–4 items, got ${ids.size}"
                )
                val reason = obj["reason"]?.jsonPrimitive?.content
                if (reason.isNullOrBlank()) throw IllegalArgumentException(
                    "'reason' is missing or blank in outfit response"
                )
                ChatResponse.WithOutfit(text, ids, reason)
            }
            else -> ChatResponse.Text(text)   // "text" + unknown types
        }
    }

    /**
     * Best-effort extraction of a JSON object from [raw].
     *
     * Handles three cases:
     * 1. Already valid JSON — returned as-is after trimming.
     * 2. JSON wrapped in a markdown code fence (```json … ```) — the inner block is extracted.
     * 3. Prose with an embedded `{…}` object — the outermost braces are extracted.
     *
     * If none of the above yield a parseable result the original trimmed string is returned
     * and the caller's [runCatching] will surface the [JsonDecodingException] naturally.
     */
    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()

        // 1. Already starts with '{' — fast path.
        if (trimmed.startsWith("{")) return trimmed

        // 2. Markdown code fence: ```json\n{…}\n``` or ```\n{…}\n```
        val fenceContent = trimmed
            .removePrefix("```json").removePrefix("```")
            .trimStart()
        if (fenceContent.startsWith("{")) {
            val end = fenceContent.lastIndexOf("```")
            return if (end > 0) fenceContent.substring(0, end).trim() else fenceContent.trim()
        }

        // 3. Extract outermost { … } from prose.
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)

        // Nothing found — let the caller's runCatching handle the parse failure.
        return trimmed
    }
}
