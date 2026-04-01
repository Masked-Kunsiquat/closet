package com.closet.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.ai.ChatResponse
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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        _uiState.update { it.copy(isIndexReady = embeddingIndex.size > 0) }
        viewModelScope.launch {
            providerSelector.providerLabel().collect { label ->
                _uiState.update { it.copy(providerLabel = label) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        _uiState.update { it.copy(isIndexReady = embeddingIndex.size > 0) }
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ChatMessage.User(text) + ChatMessage.Assistant.Thinking,
                inputText = "",
                isLoading = true,
            )
        }

        viewModelScope.launch {
            chatRepository.query(text).fold(
                onSuccess = { response ->
                    val message = response.toAssistantMessage()
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
                    val errorText = error.message ?: "Something went wrong"
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

    private suspend fun ChatResponse.toAssistantMessage(): ChatMessage.Assistant = when (this) {
        is ChatResponse.Text -> ChatMessage.Assistant.Text(text)
        is ChatResponse.WithItems -> ChatMessage.Assistant.WithItems(text, lookupItems(itemIds))
        is ChatResponse.WithOutfit -> ChatMessage.Assistant.WithOutfit(text, lookupItems(itemIds), reason)
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
