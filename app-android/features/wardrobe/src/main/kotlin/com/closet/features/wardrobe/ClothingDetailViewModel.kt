package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Clothing Detail screen.
 * Handles fetching a specific clothing item by its ID and managing the UI state.
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

    init {
        loadItem()
    }

    /**
     * Fetches the clothing item from the repository and updates the UI state.
     */
    private fun loadItem() {
        viewModelScope.launch {
            _uiState.value = ClothingDetailUiState.Loading
            
            clothingRepository.getItemById(itemId).fold(
                onLoading = { _uiState.value = ClothingDetailUiState.Loading },
                onSuccess = { item -> 
                    _uiState.value = ClothingDetailUiState.Success(item)
                },
                onError = { error ->
                    _uiState.value = ClothingDetailUiState.Error(error.message ?: "Failed to load item")
                }
            )
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
    data class Error(val message: String) : ClothingDetailUiState
}
