package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.repository.ClothingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClothingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ClothingDetailDestination>()
    val itemId = destination.itemId

    private val _uiState = MutableStateFlow<ClothingDetailUiState>(ClothingDetailUiState.Loading)
    val uiState: StateFlow<ClothingDetailUiState> = _uiState.asStateFlow()

    init {
        loadItem()
    }

    private fun loadItem() {
        viewModelScope.launch {
            try {
                val item = clothingRepository.getItemById(itemId)
                _uiState.value = if (item != null) {
                    ClothingDetailUiState.Success(item)
                } else {
                    ClothingDetailUiState.Error("Item not found")
                }
            } catch (e: Exception) {
                _uiState.value = ClothingDetailUiState.Error(e.message ?: "Failed to load item")
            }
        }
    }
}

sealed interface ClothingDetailUiState {
    data object Loading : ClothingDetailUiState
    data class Success(val item: ClothingItemWithMeta) : ClothingDetailUiState
    data class Error(val message: String) : ClothingDetailUiState
}
