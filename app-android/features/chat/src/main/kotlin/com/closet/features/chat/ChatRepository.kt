package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
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
    suspend fun query(userMessage: String): Result<ChatResponse> {
        val queryVec = encoder.encode(userMessage).getOrElse { return Result.failure(it) }
        val itemIds = index.search(queryVec, topK = 5)
        val items = if (itemIds.isEmpty()) {
            emptyList()
        } else {
            val detailMap = clothingDao.getItemDetailsByIds(itemIds).associateBy { it.item.id }
            itemIds.mapNotNull { detailMap[it] }   // restore cosine-similarity rank
        }
        val context = buildContextBlock(items)
        val provider = providerSelector.current().getOrElse { return Result.failure(it) }
        return provider.chat(userMessage, context)
    }

    private fun buildContextBlock(items: List<ClothingItemDetail>): String {
        if (items.isEmpty()) return "No wardrobe items available."
        return buildString {
            appendLine("Wardrobe context (${items.size} items):")
            items.forEachIndexed { idx, detail ->
                val item = detail.item
                val name = detail.brand?.let { "${it.name} ${item.name}" } ?: item.name
                append("${idx + 1}. [ID:${item.id}] $name")
                val category = buildString {
                    detail.category?.let { cat ->
                        append(cat.name)
                        detail.subcategory?.let { sub -> append(" > ${sub.name}") }
                    }
                }
                if (category.isNotEmpty()) append(" — $category")
                val colors = detail.colors.joinToString(", ") { it.name }
                if (colors.isNotEmpty()) append(". Colors: $colors")
                val occasions = detail.occasions.joinToString(", ") { it.name }
                if (occasions.isNotEmpty()) append(". Occasions: $occasions")
                val wearText = if (detail.wearCount == 1) "1 time" else "${detail.wearCount} times"
                append(". Worn $wearText.")
                item.imageCaption?.takeIf { it.isNotBlank() }?.let { append(" Photo: ${it.trim()}.") }
                appendLine()
            }
        }.trim()
    }
}
