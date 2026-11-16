package com.closet.core.data.ai.tools

import com.closet.core.data.ai.ChatAction
import com.closet.core.data.ai.ChatPromptPrefix
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ChatResponseParser
import com.closet.core.data.ai.ConversationTurn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Drives a [ToolCallingChatProvider]: runs the model's tool calls (at most [MAX_ROUNDS] rounds),
 * then parses the final reply and drops every item id no tool returned this turn.
 */
class ToolChatRunner(private val registry: ToolRegistry) {

    suspend fun run(
        provider: ToolCallingChatProvider,
        userMessage: String,
        history: List<ConversationTurn>,
    ): Result<ChatResponse> {
        val turns = mutableListOf<ToolTurn>()
        history.mapTo(turns) {
            when (it.role) {
                ConversationTurn.Role.User -> ToolTurn.User(it.text)
                ConversationTurn.Role.Assistant -> ToolTurn.Assistant(it.text)
            }
        }
        turns += ToolTurn.User(userMessage)

        val seenIds = mutableSetOf<Long>()
        var final: String? = null
        for (round in 0..MAX_ROUNDS) {
            val allowTools = round < MAX_ROUNDS
            val step = provider
                .step(ChatPromptPrefix.TOOL_SYSTEM_PROMPT, turns, registry.specs, allowTools)
                .getOrElse {
                    if (it is CancellationException) throw it
                    return Result.failure(it)
                }
            when (step) {
                is ToolStep.Final -> {
                    final = step.text
                    break
                }
                is ToolStep.Calls -> {
                    if (!allowTools) break
                    val results = step.calls.map { call ->
                        val (json, ids) = execute(call)
                        seenIds += ids
                        ToolCallResult(call.id, call.name, json)
                    }
                    turns += ToolTurn.Assistant(null, step.calls)
                    turns += ToolTurn.ToolResults(results)
                }
            }
        }
        val text = final
            ?: return Result.failure(IllegalStateException("Model did not produce a final answer"))
        return ChatResponseParser.parse(text).map { validate(it, seenIds) }
    }

    private suspend fun execute(call: ToolCall): Pair<JsonElement, Set<Long>> {
        val tool = registry.find(call.name) ?: return TOOL_FAILED to emptySet()
        return try {
            val result = tool.run(call.args)
            result.json to result.itemIds
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TOOL_FAILED to emptySet()
        }
    }

    private fun validate(response: ChatResponse, seen: Set<Long>): ChatResponse = when (response) {
        is ChatResponse.WithItems -> {
            val ids = response.itemIds.filter { it in seen }
            if (ids.isEmpty()) {
                ChatResponse.Text(response.text)
            } else {
                response.copy(itemIds = ids, action = response.action?.takeIf { it.isValidFor(ids) })
            }
        }
        is ChatResponse.WithOutfit -> {
            val ids = response.itemIds.filter { it in seen }
            when {
                ids.isEmpty() -> ChatResponse.Text(response.text)
                // An outfit must keep 2–4 items (ChatResponseParser); one survivor is just an item.
                ids.size < MIN_OUTFIT_ITEMS -> ChatResponse.WithItems(
                    text = response.text,
                    itemIds = ids,
                    action = response.action?.takeIf { it !is ChatAction.LogOutfit && it.isValidFor(ids) },
                )
                else -> response.copy(itemIds = ids, action = response.action?.takeIf { it.isValidFor(ids) })
            }
        }
        else -> response
    }

    /** An action may only reference items that survive validation in the final reply. */
    private fun ChatAction.isValidFor(ids: List<Long>): Boolean = when (this) {
        is ChatAction.LogOutfit -> itemIds.isNotEmpty() && itemIds.all { it in ids }
        is ChatAction.OpenItem -> itemId in ids
        ChatAction.OpenRecommendations -> true
    }

    private companion object {
        const val MAX_ROUNDS = 4
        const val MIN_OUTFIT_ITEMS = 2
        val TOOL_FAILED: JsonElement = buildJsonObject { put("error", "tool failed") }
    }
}
