package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor [ChatRouter]: no-op stub — always returns [RouterResult.Unrouted].
 *
 * ML Kit Language Identification and [kotlinx.coroutines.tasks.await] both pull in
 * GMS transitive dependencies, so routing is disabled in the FOSS build. All queries
 * fall through to the RAG + provider pipeline as usual.
 */
@Singleton
class ChatRouter @Inject constructor() {

    sealed interface RouterResult {
        data class Routed(val response: ChatResponse) : RouterResult
        data object Unrouted : RouterResult
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun route(message: String): RouterResult = RouterResult.Unrouted
}
