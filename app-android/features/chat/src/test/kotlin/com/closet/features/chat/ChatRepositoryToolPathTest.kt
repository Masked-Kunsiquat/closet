package com.closet.features.chat

import com.closet.core.data.ai.ChatAiProvider
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.ai.tools.ToolCallingChatProvider
import com.closet.core.data.ai.tools.ToolChatRunner
import com.closet.core.data.ai.tools.ToolSpec
import com.closet.core.data.ai.tools.ToolStep
import com.closet.core.data.ai.tools.ToolTurn
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryToolPathTest {

    private val router = mockk<ChatRouter>()
    private val encoder = mockk<EmbeddingEncoder>()
    private val index = mockk<EmbeddingIndex>()
    private val dao = mockk<ClothingDao>()
    private val selector = mockk<ChatAiProviderSelector>()
    private val runner = mockk<ToolChatRunner>()

    private val repo = ChatRepository(router, encoder, index, dao, selector, runner)

    private val toolReply = ChatResponse.Text("from tools")
    private val ragReply = ChatResponse.Text("from rag")

    private class ToolProvider(
        private val supports: () -> Boolean,
        private val ragReply: ChatResponse,
    ) : ChatAiProvider, ToolCallingChatProvider {
        override suspend fun supportsTools() = supports()
        override suspend fun step(
            system: String,
            turns: List<ToolTurn>,
            tools: List<ToolSpec>,
            allowTools: Boolean,
        ): Result<ToolStep> = error("not called: runner is mocked")
        override suspend fun chat(
            userMessage: String,
            context: String,
            history: List<ConversationTurn>,
        ) = Result.success(ragReply)
    }

    private fun plainProvider() = object : ChatAiProvider {
        override suspend fun chat(
            userMessage: String,
            context: String,
            history: List<ConversationTurn>,
        ) = Result.success<ChatResponse>(ragReply)
    }

    private fun stubRagPath() {
        coEvery { router.route(any()) } returns ChatRouter.RouterResult.Unrouted
        coEvery { encoder.encode(any()) } returns Result.success(FloatArray(4))
        coEvery { index.search(any(), any()) } returns emptyList()
    }

    @Test
    fun `tool capable provider routes through the runner and skips router and encoder`() = runTest {
        val provider = ToolProvider({ true }, ragReply)
        coEvery { selector.current() } returns Result.success(provider)
        coEvery { runner.run(provider, "hi", emptyList()) } returns Result.success(toolReply)

        val result = repo.query("hi")

        assertEquals(toolReply, result.getOrNull())
        coVerify(exactly = 0) { router.route(any()) }
        coVerify(exactly = 0) { encoder.encode(any()) }
    }

    @Test
    fun `provider reporting no tool support uses the router and RAG path`() = runTest {
        val provider = ToolProvider({ false }, ragReply)
        coEvery { selector.current() } returns Result.success(provider)
        stubRagPath()

        val result = repo.query("hi")

        assertEquals(ragReply, result.getOrNull())
        coVerify(exactly = 1) { router.route("hi") }
        coVerify(exactly = 0) { runner.run(any(), any(), any()) }
    }

    @Test
    fun `plain provider uses the router and RAG path`() = runTest {
        coEvery { selector.current() } returns Result.success(plainProvider())
        stubRagPath()

        val result = repo.query("hi")

        assertEquals(ragReply, result.getOrNull())
        coVerify(exactly = 1) { router.route("hi") }
        coVerify(exactly = 0) { runner.run(any(), any(), any()) }
    }

    @Test
    fun `supportsTools throwing falls back to the RAG path`() = runTest {
        val provider = ToolProvider({ throw IllegalStateException("boom") }, ragReply)
        coEvery { selector.current() } returns Result.success(provider)
        stubRagPath()

        val result = repo.query("hi")

        assertEquals(ragReply, result.getOrNull())
        coVerify(exactly = 0) { runner.run(any(), any(), any()) }
    }

    @Test
    fun `selector failure still lets routed queries answer offline`() = runTest {
        val routed = ChatResponse.Text("routed")
        coEvery { selector.current() } returns Result.failure(IllegalStateException("no key"))
        coEvery { router.route(any()) } returns ChatRouter.RouterResult.Routed(routed)

        assertEquals(routed, repo.query("hi").getOrNull())
    }

    @Test
    fun `selector failure on an unrouted query is returned`() = runTest {
        coEvery { selector.current() } returns Result.failure(IllegalStateException("no key"))
        stubRagPath()

        assertTrue(repo.query("hi").isFailure)
    }
}
