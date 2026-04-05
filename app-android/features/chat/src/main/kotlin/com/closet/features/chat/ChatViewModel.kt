package com.closet.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.ai.ConversationTurn
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.features.chat.ai.ChatAiProviderSelector
import com.closet.features.chat.model.ChatItemSummary
import com.closet.features.chat.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val clothingDao: ClothingDao,
    private val storageRepository: StorageRepository,
    private val providerSelector: ChatAiProviderSelector,
    private val embeddingIndex: EmbeddingIndex,
) : ViewModel() {

    // Rolling conversation history — max 6 turns (3 exchanges). Never persisted.
    // Only appended on successful responses; cleared by clearChat().
    private val history = mutableListOf<ConversationTurn>()

    private val _uiState = MutableStateFlow(ChatUiState(isIndexReady = embeddingIndex.size > 0))
    val uiState: StateFlow<ChatUiState> = combine(
        _uiState,
        providerSelector.providerLabel(),
    ) { state, label -> state.copy(providerLabel = label) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChatUiState(isIndexReady = embeddingIndex.size > 0),
        )

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        // Snapshot history before launching — immutable view passed to the coroutine.
        val historySnapshot = history.toList()

        _uiState.update { it.copy(isIndexReady = embeddingIndex.size > 0) }
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ChatMessage.User(text) + ChatMessage.Assistant.Thinking,
                inputText = "",
                isLoading = true,
            )
        }

        viewModelScope.launch {
            chatRepository.query(text, historySnapshot).fold(
                onSuccess = { response ->
                    val message = response.toAssistantMessage()

                    // Routed stat responses are data answers — they don't belong in conversational
                    // history. Follow-up questions on them fall through to RAG naturally.
                    if (response !is ChatResponse.WithStat) {
                        history.add(ConversationTurn(ConversationTurn.Role.User, text))
                        history.add(ConversationTurn(ConversationTurn.Role.Assistant, response.conversationText))
                        // Cap at 6 turns (3 exchanges) — drop oldest first.
                        while (history.size > 6) history.removeAt(0)
                    }

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1) + message,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    Timber.w(error, "ChatViewModel: query failed")
                    val errorText = "Something went wrong. Please try again."
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1) +
                                ChatMessage.Assistant.Error(errorText),
                            isLoading = false,
                        )
                    }
                },
            )
        }
    }

    fun clearChat() {
        history.clear()
        _uiState.update { it.copy(messages = emptyList(), inputText = "") }
    }

    private suspend fun ChatResponse.toAssistantMessage(): ChatMessage.Assistant = when (this) {
        is ChatResponse.Text -> ChatMessage.Assistant.Text(text)
        is ChatResponse.WithItems -> ChatMessage.Assistant.WithItems(text, lookupItems(itemIds))
        is ChatResponse.WithOutfit -> ChatMessage.Assistant.WithOutfit(text, lookupItems(itemIds), reason)
        is ChatResponse.WithStat -> ChatMessage.Assistant.WithStat(text, label, value, lookupItems(itemIds))
    }

    /** The conversational text of any [ChatResponse], stored as the assistant's history turn. */
    private val ChatResponse.conversationText: String
        get() = when (this) {
            is ChatResponse.Text -> text
            is ChatResponse.WithItems -> text
            is ChatResponse.WithOutfit -> text
            is ChatResponse.WithStat -> text
        }

    private suspend fun lookupItems(ids: List<Long>): List<ChatItemSummary> = try {
        val detailMap = clothingDao.getItemDetailsByIds(ids).associateBy { it.item.id }
        ids.mapNotNull { id ->
            detailMap[id]?.let { detail ->
                val name = detail.brand?.let { "${it.name} ${detail.item.name}" }
                    ?: detail.item.name
                ChatItemSummary(
                    id = id,
                    name = name,
                    imageFile = detail.item.imagePath?.let { path ->
                        storageRepository.getFile(path).takeIf { it.exists() }
                    },
                )
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "ChatViewModel: item lookup failed")
        emptyList()
    }
}
