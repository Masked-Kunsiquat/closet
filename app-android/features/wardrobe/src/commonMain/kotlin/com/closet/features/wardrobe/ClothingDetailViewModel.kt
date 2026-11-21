package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.BuildFlags
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.dao.ItemWearLog
import com.closet.core.data.model.*
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.fold
import com.closet.core.ui.util.UserMessage
import com.closet.core.ui.util.asUserMessage
import com.closet.features.wardrobe.photo.ItemPhotoProcessor
import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.wardrobe_redetect_none
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Clothing Detail screen.
 * Handles fetching a specific clothing item by its ID and managing the UI state and actions.
 */
class ClothingDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository,
    private val logRepository: LogRepository,
    private val photoProcessor: ItemPhotoProcessor,
    private val imagePathResolver: ImagePathResolver,
    private val colorRedetector: ColorRedetector,
    buildFlags: BuildFlags,
) : ViewModel() {

    /** The ID of the item being displayed, extracted from the navigation destination. */
    val itemId: Long = savedStateHandle.toRoute<ClothingDetailDestination>().itemId

    /** Whether to show the semantic debug card; debug builds only. */
    val showDebugCard: Boolean = buildFlags.isDebug

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

    private val _logWearSuccess = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Fires once after a successful [logWearToday] call — used to show a confirmation snackbar. */
    val logWearSuccess: SharedFlow<Unit> = _logWearSuccess.asSharedFlow()

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

    /** Updates the lifecycle status of the current item. */
    fun updateStatus(status: ClothingStatus) {
        viewModelScope.launch {
            clothingRepository.updateStatus(itemId, status).fold(
                onLoading = {},
                onSuccess = {},
                onError = { handleActionError(it) }
            )
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
                currentState.item.item.imagePath?.let { photoProcessor.deleteImage(it) }
            }

            clothingRepository.deleteItem(itemId).fold(
                onLoading = {},
                onSuccess = { onDeleted() },
                onError = { handleActionError(it) }
            )
        }
    }

    // --- Junction Table Updates ---

    /**
     * Replaces the item's color associations with [colorIds] (delete-then-insert). A manual edit:
     * every row is saved as [ColorSource.USER]; colours already on the item keep their measured
     * hex and share, new ones have neither. Writes nothing if [colorIds] is the same set the item
     * already has (so confirming the picker unchanged leaves auto rows open to later re-extraction).
     * The read, check and write are one transaction in [ClothingRepository.updateColorsAsUser].
     */
    fun updateColors(colorIds: List<Long>) {
        viewModelScope.launch {
            clothingRepository.updateColorsAsUser(itemId, colorIds).fold(
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

    /**
     * Logs this item as worn today without requiring a saved outfit.
     * On success, emits on [logWearSuccess] so the UI can show a confirmation snackbar.
     * On failure, emits on [actionError].
     */
    fun logWearToday() {
        viewModelScope.launch {
            logRepository.wearItemsToday(listOf(itemId)).fold(
                onLoading = {},
                onSuccess = { _logWearSuccess.tryEmit(Unit) },
                onError = { handleActionError(it) }
            )
        }
    }

    // --- Re-detect colors ---

    private val _redetectState = MutableStateFlow<RedetectState>(RedetectState.Idle)
    /** The per-item colour re-detection: measuring, then a preview the user applies or cancels. */
    val redetectState: StateFlow<RedetectState> = _redetectState.asStateFlow()

    /**
     * The extraction behind the current [RedetectState.Preview] and the photo it was measured from;
     * what [onApplyRedetect] writes (only while the item still has that photo).
     */
    private var pendingRedetect: PendingRedetect? = null
    private var redetectJob: Job? = null

    /**
     * Measures the item's photo and shows the result as a [RedetectState.Preview]. Does nothing
     * while a re-detection is already measuring or previewing, or when the item has no photo.
     * Nothing detected shows a "Couldn't detect colors" snackbar on [actionError]; a failed
     * measurement (reference shades or extraction) shows the generic error snackbar. Either way
     * it returns to [RedetectState.Idle]. When the item's photo changed while it was measured,
     * there is no preview and it returns to [RedetectState.Idle] silently.
     */
    fun onRedetectColors() {
        if (_redetectState.value != RedetectState.Idle) return
        val state = uiState.value as? ClothingDetailUiState.Success ?: return
        val imagePath = state.item.item.imagePath ?: return
        _redetectState.value = RedetectState.Measuring
        redetectJob = viewModelScope.launch {
            val proposal = colorRedetector.propose(imagePath)
            ensureActive() // cancelled while measuring: drop the result
            val measured = when (proposal) {
                is ColorRedetector.Proposal.Found -> proposal.colors
                ColorRedetector.Proposal.Empty -> emptyList()
                is ColorRedetector.Proposal.Failed -> {
                    pendingRedetect = null
                    _redetectState.value = RedetectState.Idle
                    handleActionError(proposal.cause ?: AppError.Unexpected(null))
                    return@launch
                }
            }
            val latest = uiState.value as? ClothingDetailUiState.Success ?: state
            // uiState stops tracking the item once nothing has collected it for 5 s (the screen is
            // backgrounded), so the current photo comes from the repository, not from uiState.
            val currentPath = when (val current = clothingRepository.getItemEntityById(itemId)) {
                is DataResult.Success -> current.data.imagePath
                else -> null
            }
            ensureActive()
            if (currentPath != imagePath) {
                // The photo was replaced (background removal) while it was measured, or the item
                // can no longer be read: the colours describe a photo the item may not have, so
                // show no preview.
                pendingRedetect = null
                _redetectState.value = RedetectState.Idle
                return@launch
            }
            val lookup = latest.colors.associateBy { it.id }
            // Show exactly what Apply writes: an id missing from the lookup can't be named, so drop it.
            val proposed = measured.filter { it.colorId in lookup }
            if (proposed.isEmpty()) {
                pendingRedetect = null
                _actionError.emit(UserMessage(Res.string.wardrobe_redetect_none))
                _redetectState.value = RedetectState.Idle
                return@launch
            }
            pendingRedetect = PendingRedetect(proposed, imagePath)
            _redetectState.value = RedetectState.Preview(
                current = latest.item.colors,
                proposed = proposed.map { lookup.getValue(it.colorId).copy(hex = it.hex) to it.share },
            )
        }
    }

    /** Writes the previewed colours (auto rows, curated flag cleared) and closes the preview. */
    fun onApplyRedetect() {
        if (_redetectState.value !is RedetectState.Preview) return
        val pending = pendingRedetect ?: return
        pendingRedetect = null
        _redetectState.value = RedetectState.Idle
        viewModelScope.launch {
            colorRedetector.apply(itemId, pending.colors, pending.measuredImagePath).fold(
                onLoading = {},
                onSuccess = { written -> if (written) clothingRepository.revectorizeItem(itemId) },
                onError = { handleActionError(it) }
            )
        }
    }

    /** Closes the preview (or stops a measurement) without writing anything. */
    fun onCancelRedetect() {
        redetectJob?.cancel()
        redetectJob = null
        pendingRedetect = null
        _redetectState.value = RedetectState.Idle
    }

    /** Resolves a stored relative image [path] to an absolute path, or null if [path] is null. */
    fun resolveImagePath(path: String?): String? = path?.let { imagePathResolver.absolutePath(it) }

    private fun handleActionError(throwable: Throwable) {
        val error = throwable as? AppError ?: AppError.Unexpected(throwable)
        viewModelScope.launch {
            _actionError.emit(error.asUserMessage())
        }
    }
}

/** A previewed re-detection: the measured [colors] and the photo ([measuredImagePath]) they came from. */
private data class PendingRedetect(val colors: List<ExtractedColor>, val measuredImagePath: String)

/** State of the per-item colour re-detection on the detail screen. */
sealed interface RedetectState {
    data object Idle : RedetectState
    data object Measuring : RedetectState
    /**
     * The colours the item has now ([current], lookup swatches) and the measured proposal: each
     * lookup colour carries the *measured* hex (for its swatch) and is paired with its share (0..1).
     */
    data class Preview(
        val current: List<ColorEntity>,
        val proposed: List<Pair<ColorEntity, Double>>,
    ) : RedetectState
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
