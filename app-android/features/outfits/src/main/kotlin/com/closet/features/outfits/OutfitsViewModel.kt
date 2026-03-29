package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.OutfitWithItems
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * One-time events emitted by [OutfitsViewModel] after a wear-log or delete operation completes.
 */
sealed interface OutfitsEvent {
    /** Emitted when the outfit was successfully logged as worn today. */
    data object WearSuccess : OutfitsEvent
    /** Emitted when the wear-log operation failed. */
    data object WearError : OutfitsEvent
    /** Emitted when the outfit was successfully deleted. */
    data object DeleteSuccess : OutfitsEvent
    /** Emitted when the delete operation failed. */
    data object DeleteError : OutfitsEvent
}

/**
 * ViewModel for the Outfits gallery screen.
 *
 * Exposes the full list of saved outfits as a live [outfits] flow and handles
 * the "Wear Today" action via [wearOutfit]. Only one wear operation is allowed
 * in flight at a time; [wearingOutfitId] tracks which card shows a loading state.
 */
@HiltViewModel
class OutfitsViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val logRepository: LogRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    /** Live list of all saved outfits with their member items. */
    val outfits: StateFlow<List<OutfitWithItems>> = outfitRepository.getAllOutfitsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ID of the outfit currently being logged; null when idle.
    // Drives per-card button loading/disabled state.
    private val _wearingOutfitId = MutableStateFlow<Long?>(null)
    /** ID of the outfit currently being logged as worn; null when idle. Drives per-card loading state. */
    val wearingOutfitId: StateFlow<Long?> = _wearingOutfitId.asStateFlow()

    private val _events = Channel<OutfitsEvent>(Channel.BUFFERED)
    /** One-time [OutfitsEvent] emissions for the screen to collect. */
    val events = _events.receiveAsFlow()

    /** Resolves a stored relative image [path] to a [File], or null if [path] is null. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

    /** Permanently deletes the outfit with [outfitId]. */
    fun deleteOutfit(outfitId: Long) {
        viewModelScope.launch {
            when (outfitRepository.deleteOutfit(outfitId)) {
                is DataResult.Success -> _events.send(OutfitsEvent.DeleteSuccess)
                is DataResult.Error -> _events.send(OutfitsEvent.DeleteError)
                else -> Unit
            }
        }
    }

    /**
     * Logs [outfitId] as worn today. Only one log operation runs at a time;
     * subsequent calls while [wearingOutfitId] is non-null are ignored.
     */
    fun wearOutfit(outfitId: Long) {
        if (_wearingOutfitId.value != null) return
        viewModelScope.launch {
            _wearingOutfitId.value = outfitId
            val result = logRepository.wearOutfitToday(outfitId)
            _wearingOutfitId.value = null
            when (result) {
                is DataResult.Success -> _events.send(OutfitsEvent.WearSuccess)
                is DataResult.Error -> _events.send(OutfitsEvent.WearError)
                else -> Unit
            }
        }
    }
}
