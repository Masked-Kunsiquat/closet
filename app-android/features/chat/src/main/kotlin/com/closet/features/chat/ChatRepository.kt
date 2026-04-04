package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full RAG query pipeline:
 * encode query → retrieve top-K items → build context block → call AI provider.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val encoder: EmbeddingEncoder,
    private val index: EmbeddingIndex,
    private val clothingDao: ClothingDao,
    private val providerSelector: ChatAiProviderSelector,
) {
    suspend fun query(
        userMessage: String,
        history: List<ConversationTurn> = emptyList(),
    ): Result<ChatResponse> {
        return try {
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
            val provider = providerSelector.current().getOrElse {
                if (it is CancellationException) throw it
                return Result.failure(it)
            }
            val chatResult = provider.chat(userMessage, context, history)
            chatResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            chatResult
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun buildContextBlock(items: List<ClothingItemDetail>): String {
        if (items.isEmpty()) return "No wardrobe items available."
        return buildString {
            appendLine("Wardrobe context (${items.size} items):")
            items.forEachIndexed { idx, detail ->
                val item = detail.item
                val name = (detail.brand?.let { "${it.name} ${item.name}" } ?: item.name)
                    .replace(Regex("\\s+"), " ").trim()
                append("${idx + 1}. [ID:${item.id}] $name")
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
