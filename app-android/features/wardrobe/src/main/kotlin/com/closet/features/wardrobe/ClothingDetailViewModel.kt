package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.dao.ItemWearLog
import com.closet.core.data.model.*
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.AppError
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
    private val logRepository: LogRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ClothingDetailDestination>()
    /** The ID of the item being displayed, extracted from the navigation destination. */
    val itemId = destination.itemId

    private val itemDetailFlow = clothingRepository.getItemDetail(itemId)

    private val lookupFlow = combine(
        lookupRepository.getColors(),
        lookupRepository.getMaterials(),
        lookupRepository.getSeasons(),
        lookupRepository.getOccasions(),
        lookupRepository.getPatterns(),
        lookupRepository.getSizeSystems()
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        ClothingDetailLookup(
            colors = args[0] as List<ColorEntity>,
            materials = args[1] as List<MaterialEntity>,
            seasons = args[2] as List<SeasonEntity>,
            occasions = args[3] as List<OccasionEntity>,
            patterns = args[4] as List<PatternEntity>,
            sizeSystems = args[5] as List<SizeSystemEntity>
        )
    }

    private val wearHistoryFlow = logRepository.getLogsForItem(itemId)

    /** UI state combining item detail, lookup lists, and wear history. */
    val uiState: StateFlow<ClothingDetailUiState> = combine(
        itemDetailFlow, lookupFlow, wearHistoryFlow
    ) { detail, lookup, history ->
        if (detail != null) {
            ClothingDetailUiState.Success(
                item = detail,
                colors = lookup.colors,
                materials = lookup.materials,
                seasons = lookup.seasons,
                occasions = lookup.occasions,
                patterns = lookup.patterns,
                sizeSystems = lookup.sizeSystems,
                wearHistory = history,
            )
        } else {
            ClothingDetailUiState.Error(AppError.DatabaseError.NotFound().asUserMessage())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClothingDetailUiState.Loading)

    private val _actionError = MutableSharedFlow<UserMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** One-shot error events emitted when a quick action (toggle, delete, update) fails. */
    val actionError: SharedFlow<UserMessage> = _actionError.asSharedFlow()

    /** Toggles the favorite status of the current item. */
    fun toggleFavorite() {
        viewModelScope.launch {
            val currentState = uiState.value
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

    /** Toggles the wash status between [WashStatus.Clean] and [WashStatus.Dirty]. */
    fun toggleWashStatus() {
        viewModelScope.launch {
            val currentState = uiState.value
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

    /**
     * Deletes the item and its associated image file, then calls [onDeleted] on success.
     * Emits [actionError] if the delete fails.
     */
    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val currentState = uiState.value
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

    /** Replaces the item's color associations with [colorIds] (delete-then-insert). */
    fun updateColors(colorIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemColors(itemId, colorIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    /** Replaces the item's material associations with [materialIds] (delete-then-insert). */
    fun updateMaterials(materialIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemMaterials(itemId, materialIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    /** Replaces the item's season associations with [seasonIds] (delete-then-insert). */
    fun updateSeasons(seasonIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemSeasons(itemId, seasonIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    /** Replaces the item's occasion associations with [occasionIds] (delete-then-insert). */
    fun updateOccasions(occasionIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemOccasions(itemId, occasionIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    /** Replaces the item's pattern associations with [patternIds] (delete-then-insert). */
    fun updatePatterns(patternIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateItemPatterns(itemId, patternIds).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
        }
    }

    /** Resolves a stored relative image [path] to a [File], or null if [path] is null. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

    private fun handleActionError(throwable: Throwable) {
        val error = throwable as? AppError ?: AppError.Unexpected(throwable)
        viewModelScope.launch {
            _actionError.emit(error.asUserMessage())
        }
    }
}

private data class ClothingDetailLookup(
    val colors: List<ColorEntity>,
    val materials: List<MaterialEntity>,
    val seasons: List<SeasonEntity>,
    val occasions: List<OccasionEntity>,
    val patterns: List<PatternEntity>,
    val sizeSystems: List<SizeSystemEntity>
)

/**
 * UI state for the Clothing Detail screen.
 */
sealed interface ClothingDetailUiState {
    data object Loading : ClothingDetailUiState
    data class Success(
        val item: ClothingItemDetail,
        val colors: List<ColorEntity> = emptyList(),
        val materials: List<MaterialEntity> = emptyList(),
        val seasons: List<SeasonEntity> = emptyList(),
        val occasions: List<OccasionEntity> = emptyList(),
        val patterns: List<PatternEntity> = emptyList(),
        val sizeSystems: List<SizeSystemEntity> = emptyList(),
        val wearHistory: List<ItemWearLog> = emptyList(),
    ) : ClothingDetailUiState
    data class Error(val userMessage: UserMessage) : ClothingDetailUiState
}
