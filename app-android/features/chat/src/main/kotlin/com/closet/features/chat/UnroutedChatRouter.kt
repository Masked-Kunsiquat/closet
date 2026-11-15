package com.closet.features.chat

/**
 * No-op [ChatRouter] — always returns [ChatRouter.RouterResult.Unrouted].
 *
 * Used where language identification is unavailable (the FOSS build), so all queries
 * fall through to the RAG + provider pipeline as usual.
 */
class UnroutedChatRouter : ChatRouter {
    @Suppress("RedundantSuspendModifier")
    override suspend fun route(message: String): ChatRouter.RouterResult = ChatRouter.RouterResult.Unrouted
}
