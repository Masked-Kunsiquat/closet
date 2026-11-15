package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse

/** Routes data-only chat queries straight from DAO results, bypassing the RAG + provider pipeline. */
interface ChatRouter {

    sealed interface RouterResult {
        data class Routed(val response: ChatResponse) : RouterResult
        data object Unrouted : RouterResult
    }

    suspend fun route(message: String): RouterResult
}
