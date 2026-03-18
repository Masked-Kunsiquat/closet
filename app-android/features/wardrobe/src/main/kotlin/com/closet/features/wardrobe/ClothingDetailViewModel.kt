package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.fold
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Clothing Detail screen.
 * Handles fetching a specific clothing item by its ID and managing the UI state and actions.
 *
 * @param savedStateHandle Handle to saved state, used to retrieve navigation arguments.
 * @param clothingRepository Repository for accessing clothing item data.
 */
@HiltViewModel
class ClothingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ClothingDetailDestination>()

    /** The ID of the clothing item being displayed. */
    val itemId = destination.itemId

    private val _uiState = MutableStateFlow<ClothingDetailUiState>(ClothingDetailUiState.Loading)

    /** The current UI state of the detail screen. */
    val uiState: StateFlow<ClothingDetailUiState> = _uiState.asStateFlow()

    private val _actionError = MutableSharedFlow<UserMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Transient error messages for actions like toggle or delete. */
    val actionError: SharedFlow<UserMessage> = _actionError.asSharedFlow()

    init {
        loadItem()
    }

    /**
     * Fetches the clothing item from the repository and updates the UI state.
     * Uses the type-safe [DataResult] to handle success and various error states.
     */
    fun loadItem() {
        viewModelScope.launch {
            _uiState.value = ClothingDetailUiState.Loading

            clothingRepository.getItemById(itemId).fold(
                onLoading = { /* Suspended results won't trigger onLoading here */ },
                onSuccess = { item ->
                    _uiState.value = ClothingDetailUiState.Success(item)
                },
                onError = { throwable ->
                    val error = throwable as? AppError ?: AppError.Unexpected(throwable)
                    _uiState.value = ClothingDetailUiState.Error(error.asUserMessage())
                }
            )
        }
    }

    /**
     * Toggles the favorite status of the current item.
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ClothingDetailUiState.Success) {
                val newFavorite = currentState.item.isFavorite == 0
                clothingRepository.updateFavoriteStatus(itemId, newFavorite).fold(
                    onLoading = {},
                    onSuccess = { loadItem() }, // Refresh item to show updated state
                    onError = { handleActionError(it) }
                )
            }
        }
    }

    /**
     * Toggles the wash status of the current item between Clean and Dirty.
     */
    fun toggleWashStatus() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ClothingDetailUiState.Success) {
                val newStatus = if (currentState.item.washStatus == WashStatus.Clean) {
                    WashStatus.Dirty
                } else {
                    WashStatus.Clean
                }
                clothingRepository.updateWashStatus(itemId, newStatus).fold(
                    onLoading = {},
                    onSuccess = { loadItem() },
                    onError = { handleActionError(it) }
                )
            }
        }
    }

    /**
     * Deletes the current clothing item and invokes the callback on success.
     * @param onDeleted Callback to navigate back or show a message.
     */
    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            clothingRepository.deleteItem(itemId).fold(
                onLoading = {},
                onSuccess = { onDeleted() },
                onError = { handleActionError(it) }
            )
        }
    }

    /**
     * Maps action errors to a transient event flow rather than replacing the whole UI state.
     */
    private fun handleActionError(throwable: Throwable) {
        val error = throwable as? AppError ?: AppError.Unexpected(throwable)
        viewModelScope.launch {
            _actionError.emit(error.asUserMessage())
        }
    }
}

/**
 * UI state for the Clothing Detail screen.
 */
sealed interface ClothingDetailUiState {
    /** Indicates the item is currently being fetched. */
    data object Loading : ClothingDetailUiState

    /** Indicates the item was successfully retrieved. */
    data class Success(val item: ClothingItemWithMeta) : ClothingDetailUiState

    /** Indicates a failure occurred while fetching the item. */
    data class Error(val userMessage: UserMessage) : ClothingDetailUiState
}
