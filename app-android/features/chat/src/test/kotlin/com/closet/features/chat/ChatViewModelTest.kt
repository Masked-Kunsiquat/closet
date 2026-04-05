package com.closet.features.chat

import app.cash.turbine.test
import com.closet.core.data.ai.ChatAction
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
import com.closet.features.chat.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val chatRepository = mockk<ChatRepository>()
    private val clothingDao = mockk<ClothingDao>()
    private val storageRepository = mockk<StorageRepository>(relaxed = true)
    private val providerSelector = mockk<ChatAiProviderSelector>()
    private val embeddingIndex = mockk<EmbeddingIndex>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // MutableStateFlow never completes, so combine() stays alive for the full
        // test. "AI" matches the default providerLabel in ChatUiState so the first
        // combine emission equals the stateIn initial — StateFlow deduplication
        // means Turbine sees exactly one item on subscribe.
        every { providerSelector.providerLabel() } returns MutableStateFlow("AI")
        every { embeddingIndex.size } returns 10
        coEvery { clothingDao.getItemDetailsByIds(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds the ViewModel and immediately starts a background collector so that
     * [SharingStarted.WhileSubscribed] activates the upstream `combine` before the
     * test's `test {}` block subscribes. Without this, the sharing coroutine is still
     * queued in the scheduler when `sendMessage()` emits its first state update, causing
     * Turbine's `awaitItem()` to receive the stale initial state instead of the live one.
     */
    private fun TestScope.buildViewModel(): ChatViewModel {
        val vm = ChatViewModel(
            chatRepository, clothingDao, storageRepository, providerSelector, embeddingIndex
        )
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        return vm
    }

    /** Sets input text and sends — mirrors the UI user flow. */
    private fun ChatViewModel.send(text: String = "what should I wear?") {
        onInputChanged(text)
        sendMessage()
    }

    private fun givenSuccess(response: ChatResponse = ChatResponse.Text("ok")) {
        coEvery { chatRepository.query(any(), any()) } returns Result.success(response)
    }

    private fun givenFailure() {
        coEvery { chatRepository.query(any(), any()) } returns Result.failure(RuntimeException("fail"))
    }

    // ─── Message flow ─────────────────────────────────────────────────────────

    @Test
    fun `sendMessage with blank input does not call repository`() = runTest(testDispatcher) {
        givenSuccess()
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem() // initial state — inputText is ""

            vm.sendMessage() // text.trim() == "" → blank → early return

            expectNoEvents()
            coVerify(exactly = 0) { chatRepository.query(any(), any()) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage while loading is ignored`() = runTest(testDispatcher) {
        // StandardTestDispatcher queues launched coroutines without running them eagerly.
        // This lets us check the isLoading guard before the query coroutine fires.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        givenSuccess()
        val vm = buildViewModel()

        vm.send("first message") // isLoading → true; query coroutine queued, not yet run

        vm.onInputChanged("second message")
        vm.sendMessage() // isLoading=true → early return; repository must not be called again

        advanceUntilIdle() // run the queued coroutine

        coVerify(exactly = 1) { chatRepository.query(any(), any()) }
    }

    @Test
    fun `successful response replaces Thinking with assistant message and clears loading`() = runTest(testDispatcher) {
        givenSuccess(ChatResponse.Text("great choice"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.messages.size)
        assertTrue(state.messages[0] is ChatMessage.User)
        assertTrue(state.messages[1] is ChatMessage.Assistant.Text)
    }

    @Test
    fun `failed response replaces Thinking with Error message and clears loading`() = runTest(testDispatcher) {
        givenFailure()
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.messages.last() is ChatMessage.Assistant.Error)
    }

    // ─── History — Phase 1 ────────────────────────────────────────────────────

    @Test
    fun `successful response adds 2 turns to history`() = runTest(testDispatcher) {
        val historySlot = slot<List<ConversationTurn>>()
        coEvery { chatRepository.query(any(), capture(historySlot)) } returns
            Result.success(ChatResponse.Text("ok"))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()

            vm.send("first question")
            awaitItem()

            vm.send("follow-up")
            awaitItem()

            // Slot now holds the history snapshot passed on the second send:
            // [User("first question"), Assistant("ok")]
            assertEquals(2, historySlot.captured.size)
            assertEquals(ConversationTurn.Role.User, historySlot.captured[0].role)
            assertEquals("first question", historySlot.captured[0].text)
            assertEquals(ConversationTurn.Role.Assistant, historySlot.captured[1].role)
            assertEquals("ok", historySlot.captured[1].text)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed response does not add to history`() = runTest(testDispatcher) {
        val historySlot = slot<List<ConversationTurn>>()
        coEvery { chatRepository.query(any(), capture(historySlot)) } returns
            Result.failure(RuntimeException("fail"))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()

            vm.send("first")
            awaitItem()

            vm.send("second")
            awaitItem()

            // Both sends failed — history was never updated, second send passes empty list
            assertTrue(historySlot.captured.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history is capped at 6 turns after 3 exchanges`() = runTest(testDispatcher) {
        val historySlot = slot<List<ConversationTurn>>()
        coEvery { chatRepository.query(any(), capture(historySlot)) } returns
            Result.success(ChatResponse.Text("ok"))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()

            // 3 exchanges fills history to the 6-turn cap
            repeat(3) { i ->
                vm.send("q${i + 1}")
                awaitItem()
            }

            // 4th exchange: slot captures the 6 turns from 3 prior exchanges
            vm.send("q4")
            awaitItem()
            assertEquals(6, historySlot.captured.size)

            // 5th exchange: oldest pair was dropped after exchange 4 — still 6
            vm.send("q5")
            awaitItem()
            assertEquals(6, historySlot.captured.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── History — Phase 2 stat responses ────────────────────────────────────

    @Test
    fun `WithStat response does not add to history`() = runTest(testDispatcher) {
        val historySlot = slot<List<ConversationTurn>>()
        coEvery { chatRepository.query(any(), capture(historySlot)) } returns Result.success(
            ChatResponse.WithStat("You have 12 items", "Wardrobe size", "12 items")
        )
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()

            vm.send("how many items do I own?")
            awaitItem()

            vm.send("follow-up question")
            awaitItem()

            // Stat responses never update history — follow-up receives empty list
            assertTrue(historySlot.captured.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── clearChat ────────────────────────────────────────────────────────────

    @Test
    fun `clearChat resets messages and inputText`() = runTest(testDispatcher) {
        givenSuccess()
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        vm.clearChat()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals("", state.inputText)
    }

    @Test
    fun `clearChat resets history`() = runTest(testDispatcher) {
        val historySlot = slot<List<ConversationTurn>>()
        coEvery { chatRepository.query(any(), capture(historySlot)) } returns
            Result.success(ChatResponse.Text("ok"))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()

            vm.send("before clear")
            awaitItem()

            vm.clearChat()
            awaitItem()

            vm.send("after clear")
            awaitItem()

            // After clearChat(), history was reset — query receives empty list
            assertTrue(historySlot.captured.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── toAssistantMessage mapping ───────────────────────────────────────────

    @Test
    fun `Text response maps to ChatMessage Assistant Text`() = runTest(testDispatcher) {
        givenSuccess(ChatResponse.Text("great choice"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val msg = vm.uiState.value.messages.last()
        assertTrue(msg is ChatMessage.Assistant.Text)
        assertEquals("great choice", (msg as ChatMessage.Assistant.Text).text)
    }

    @Test
    fun `WithItems response action is preserved in ChatMessage`() = runTest(testDispatcher) {
        val action = ChatAction.OpenRecommendations
        givenSuccess(ChatResponse.WithItems("check these out", listOf(1L), action))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val msg = vm.uiState.value.messages.last() as ChatMessage.Assistant.WithItems
        assertEquals(action, msg.action)
    }

    @Test
    fun `WithOutfit response action is preserved in ChatMessage`() = runTest(testDispatcher) {
        val action = ChatAction.LogOutfit(listOf(1L, 2L))
        givenSuccess(ChatResponse.WithOutfit("nice look", listOf(1L, 2L), "great combo", action))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val msg = vm.uiState.value.messages.last() as ChatMessage.Assistant.WithOutfit
        assertEquals(action, msg.action)
    }

    @Test
    fun `WithStat response maps to ChatMessage Assistant WithStat`() = runTest(testDispatcher) {
        givenSuccess(ChatResponse.WithStat("12 items", "Wardrobe size", "12 items"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.send()
        advanceUntilIdle()

        val msg = vm.uiState.value.messages.last()
        assertTrue(msg is ChatMessage.Assistant.WithStat)
    }
}
