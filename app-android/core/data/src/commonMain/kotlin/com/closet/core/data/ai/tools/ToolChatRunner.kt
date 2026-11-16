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
                response.copy(itemIds = ids, action = response.action?.takeIf { it.isValid(seen) })
            }
        }
        is ChatResponse.WithOutfit -> {
            val ids = response.itemIds.filter { it in seen }
            if (ids.isEmpty()) {
                ChatResponse.Text(response.text)
            } else {
                response.copy(itemIds = ids, action = response.action?.takeIf { it.isValid(seen) })
            }
        }
        else -> response
    }

    private fun ChatAction.isValid(seen: Set<Long>): Boolean = when (this) {
        is ChatAction.LogOutfit -> itemIds.all { it in seen }
        is ChatAction.OpenItem -> itemId in seen
        ChatAction.OpenRecommendations -> true
    }

    private companion object {
        const val MAX_ROUNDS = 4
        val TOOL_FAILED: JsonElement = buildJsonObject { put("error", "tool failed") }
    }
}
