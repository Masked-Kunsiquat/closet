package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.palette.graphics.Palette
import com.closet.core.data.model.*
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.PaletteUtil
import com.closet.core.data.util.fold
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the Clothing Detail screen.
 * Handles fetching a specific clothing item by its ID and managing the UI state and actions.
 */
@HiltViewModel
class ClothingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ClothingDetailDestination>()
    val itemId = destination.itemId

    // Detailed item flow
    private val itemDetailFlow = clothingRepository.getItemDetail(itemId)

    // Lookup data flows
    val colors = lookupRepository.getColors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val materials = lookupRepository.getMaterials().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seasons = lookupRepository.getSeasons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val occasions = lookupRepository.getOccasions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val patterns = lookupRepository.getPatterns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<ClothingDetailUiState>(ClothingDetailUiState.Loading)
    val uiState: StateFlow<ClothingDetailUiState> = _uiState.asStateFlow()

    private val _actionError = MutableSharedFlow<UserMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actionError: SharedFlow<UserMessage> = _actionError.asSharedFlow()

    private val _palette = MutableStateFlow<Palette?>(null)
    val palette: StateFlow<Palette?> = _palette.asStateFlow()

    init {
        viewModelScope.launch {
            itemDetailFlow.collect { detail ->
                if (detail != null) {
                    _uiState.value = ClothingDetailUiState.Success(detail)
                    extractPalette(detail.item.imagePath)
                } else {
                    _uiState.value = ClothingDetailUiState.Error(AppError.DatabaseError.NotFound().asUserMessage())
                }
            }
        }
    }

    private fun extractPalette(imagePath: String?) {
        if (imagePath == null) return
        viewModelScope.launch {
            val file = storageRepository.getFile(imagePath)
            _palette.value = PaletteUtil.extractPalette(file)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ClothingDetailUiState.Success) {
                val newFavorite = currentState.item.item.isFavorite == 0
                clothingRepository.updateFavoriteStatus(itemId, newFavorite).fold(
                    onLoading = {},
                    onSuccess = { /* Flow will update automatically */ },
                    onError = { handleActionError(it) }
                )
            }
        }
    }

    fun toggleWashStatus() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ClothingDetailUiState.Success) {
                val newStatus = if (currentState.item.item.washStatus == WashStatus.Clean) {
                    WashStatus.Dirty
                } else {
                    WashStatus.Clean
                }
                clothingRepository.updateWashStatus(itemId, newStatus).fold(
                    onLoading = {},
                    onSuccess = { /* Flow will update */ },
                    onError = { handleActionError(it) }
                )
            }
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ClothingDetailUiState.Success) {
                currentState.item.item.imagePath?.let { storageRepository.deleteImage(it) }
            }

            clothingRepository.deleteItem(itemId).fold(
                onLoading = {},
                onSuccess = { onDeleted() },
                onError = { handleActionError(it) }
            )
        }
    }

    // --- Junction Table Updates ---

    fun updateColors(colorIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemColors(itemId, colorIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    fun updateMaterials(materialIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemMaterials(itemId, materialIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    fun updateSeasons(seasonIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemSeasons(itemId, seasonIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    fun updateOccasions(occasionIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemOccasions(itemId, occasionIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    fun updatePatterns(patternIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemPatterns(itemId, patternIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    fun getAbsoluteFile(relativePath: String): File {
        return storageRepository.getFile(relativePath)
    }

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
    data object Loading : ClothingDetailUiState
    data class Success(val item: ClothingItemDetail) : ClothingDetailUiState
    data class Error(val userMessage: UserMessage) : ClothingDetailUiState
}
