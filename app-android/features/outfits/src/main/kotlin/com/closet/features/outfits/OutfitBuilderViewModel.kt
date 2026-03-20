package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OutfitBuilderUiState(
    val name: String = "",
    val members: List<OutfitMember> = emptyList(),
    val isSaving: Boolean = false
) {
    val canSave: Boolean get() = members.isNotEmpty() && !isSaving
}

sealed interface OutfitBuilderEvent {
    data object SaveSuccess : OutfitBuilderEvent
    data object SaveError : OutfitBuilderEvent
}

@HiltViewModel
class OutfitBuilderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository,
    private val outfitRepository: OutfitRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }

    private val _name = MutableStateFlow("")
    private val _memberIds = MutableStateFlow<List<Long>>(emptyList())
    private val _isSaving = MutableStateFlow(false)

    private val _events = Channel<OutfitBuilderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Unfiltered item list used to resolve member IDs → ClothingItemDetail.
    private val _allItems: StateFlow<List<ClothingItemDetail>> = clothingRepository.getAllItemDetails()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * Currently selected outfit members, ordered by selection time.
     * Layout is always null in V1 (no canvas placement yet).
     */
    val members: StateFlow<List<OutfitMember>> = combine(_allItems, _memberIds) { items, ids ->
        val byId = items.associateBy { it.item.id }
        ids.mapNotNull { id -> byId[id]?.let { OutfitMember(it, layout = null) } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList()
    )

    val uiState: StateFlow<OutfitBuilderUiState> = combine(
        _name, members, _isSaving
    ) { name, members, isSaving ->
        OutfitBuilderUiState(name = name, members = members, isSaving = isSaving)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = OutfitBuilderUiState()
    )

    init {
        // Observe picker results deposited by WardrobePickerScreen into this destination's
        // SavedStateHandle via navController.previousBackStackEntry?.savedStateHandle.
        savedStateHandle.getStateFlow(PICKER_RESULT_KEY, LongArray(0))
            .onEach { ids ->
                if (ids.isNotEmpty()) {
                    addItems(ids.toList())
                    savedStateHandle[PICKER_RESULT_KEY] = LongArray(0) // consume
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateName(value: String) {
        _name.value = value
    }

    /**
     * Adds items by ID, preserving order and skipping duplicates.
     */
    fun addItems(ids: List<Long>) {
        val current = _memberIds.value.toMutableList()
        ids.forEach { id -> if (id !in current) current.add(id) }
        _memberIds.value = current
    }

    fun removeMember(itemId: Long) {
        _memberIds.value = _memberIds.value.filter { it != itemId }
    }

    fun save() {
        // Derive IDs from the resolved member list, not raw _memberIds, so any item that was
        // deleted from the wardrobe while the builder was open is already absent here.
        val resolvedIds = members.value.map { it.item.item.id }
        if (_isSaving.value || resolvedIds.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = outfitRepository.createOutfit(
                name = _name.value.trim().ifEmpty { null },
                notes = null,
                itemIds = resolvedIds
            )
            _isSaving.value = false
            when (result) {
                is DataResult.Success -> _events.send(OutfitBuilderEvent.SaveSuccess)
                is DataResult.Error -> _events.send(OutfitBuilderEvent.SaveError)
                DataResult.Loading -> Unit
            }
        }
    }

    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }
}
