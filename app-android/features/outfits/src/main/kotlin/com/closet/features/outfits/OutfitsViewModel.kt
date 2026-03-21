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

sealed interface OutfitsEvent {
    data object WearSuccess : OutfitsEvent
    data object WearError : OutfitsEvent
}

@HiltViewModel
class OutfitsViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val logRepository: LogRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    val outfits: StateFlow<List<OutfitWithItems>> = outfitRepository.getAllOutfitsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ID of the outfit currently being logged; null when idle.
    // Drives per-card button loading/disabled state.
    private val _wearingOutfitId = MutableStateFlow<Long?>(null)
    val wearingOutfitId: StateFlow<Long?> = _wearingOutfitId.asStateFlow()

    private val _events = Channel<OutfitsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

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
