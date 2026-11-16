package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.tools.ToolCallingChatProvider
import com.closet.core.data.ai.tools.ToolChatRunner
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
import kotlinx.coroutines.CancellationException

/**
 * Chat entry point. When the selected provider is a [ToolCallingChatProvider] that reports
 * [ToolCallingChatProvider.supportsTools], the query goes through [ToolChatRunner] and neither the
 * router nor the RAG pipeline runs. Otherwise (and if provider selection fails) the RAG path below
 * runs unchanged, so routed stat queries still answer offline with no provider configured.
 *
 * RAG query pipeline:
 * route → (if unrouted) encode query → retrieve top-K items → build context block → call AI provider.
 *
 * [ChatRouter] is checked first. On a [ChatRouter.RouterResult.Routed] result the response is
 * returned immediately — the encoder, index, and provider are never touched. This keeps routed
 * responses zero-token-cost and fully offline.
 *
 * In the FOSS flavor [ChatRouter] is a no-op stub, so all queries go through the full pipeline.
 */
class ChatRepository(
    private val router: ChatRouter,
    private val encoder: EmbeddingEncoder,
    private val index: EmbeddingIndex,
    private val clothingDao: ClothingDao,
    private val providerSelector: ChatAiProviderSelector,
    private val toolRunner: ToolChatRunner,
) {
    suspend fun query(
        userMessage: String,
        history: List<ConversationTurn> = emptyList(),
    ): Result<ChatResponse> {
        return try {
            // Select first: Nano availability can change per query. A selection failure is not
            // fatal yet - the router may still answer, and the RAG path reports it otherwise.
            val selected = providerSelector.current()
            selected.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val selectedProvider = selected.getOrNull()
            if (selectedProvider is ToolCallingChatProvider && supportsToolsSafely(selectedProvider)) {
                val toolResult = toolRunner.run(selectedProvider, userMessage, history)
                toolResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                return toolResult
            }

            // Router check — short-circuits the RAG pipeline for stat queries.
            when (val routed = router.route(userMessage)) {
                is ChatRouter.RouterResult.Routed -> return Result.success(routed.response)
                is ChatRouter.RouterResult.Unrouted -> Unit
            }

            val queryVec = encoder.encode(userMessage).getOrElse {
                if (it is CancellationException) throw it
                return Result.failure(it)
            }
            val itemIds = index.search(queryVec, topK = 5)
            val items = if (itemIds.isEmpty()) {
                emptyList()
            } else {
                val detailMap = clothingDao.getItemDetailsByIds(itemIds).associateBy { it.item.id }
                itemIds.mapNotNull { detailMap[it] }   // restore cosine-similarity rank
            }
            // Context is built once and injected into the provider's system message only.
            // History turns are passed separately — providers never re-inject context per turn.
            val context = buildContextBlock(items)
            val provider = selected.getOrElse { return Result.failure(it) }
            val chatResult = provider.chat(userMessage, context, history)
            chatResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            chatResult
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun supportsToolsSafely(provider: ToolCallingChatProvider): Boolean =
        try {
            provider.supportsTools()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }

    internal fun buildContextBlock(items: List<ClothingItemDetail>): String {
        if (items.isEmpty()) return "No wardrobe items available."
        return buildString {
            appendLine("Wardrobe context (${items.size} items):")
            items.forEach { detail ->
                val item = detail.item
                val name = (detail.brand?.let { "${it.name} ${item.name}" } ?: item.name)
                    .replace(Regex("\\s+"), " ").trim()
                // No positional number: small models confuse a list index with the [ID:…].
                append("- [ID:${item.id}] $name")
                val category = buildString {
                    detail.category?.let { cat -> append(cat.name) }
                    detail.subcategory?.let { sub ->
                        if (isNotEmpty()) append(" > ")
                        append(sub.name)
                    }
                }
                if (category.isNotEmpty()) append(" ($category)")
                if (detail.colors.isNotEmpty()) {
                    append(", Colors: ${detail.colors.joinToString { it.name }}")
                }
                if (detail.materials.isNotEmpty()) {
                    append(", Materials: ${detail.materials.joinToString { it.name }}")
                }
                val descText = item.semanticDescription
                    ?.substringBefore("Notes:")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
                val captionText = item.imageCaption?.trim().orEmpty()
                if (descText.isNotEmpty()) append(". $descText")
                if (captionText.isNotEmpty()) append(". $captionText")
                appendLine()
            }
        }.trim()
    }
}
