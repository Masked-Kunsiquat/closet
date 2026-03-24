package com.closet.features.outfits

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI state for the Outfit Builder screen.
 *
 * @property name Optional outfit name entered by the user.
 * @property members Clothing items currently added to the outfit, ordered by selection time.
 * @property isSaving True while the save operation is in flight.
 * @property canSave Derived flag — true when there is at least one member and no save is pending.
 */
data class OutfitBuilderUiState(
    val name: String = "",
    val members: List<OutfitMember> = emptyList(),
    val isSaving: Boolean = false
) {
    val canSave: Boolean get() = members.isNotEmpty() && !isSaving
}

/**
 * One-time events emitted by [OutfitBuilderViewModel] for the screen to handle via a channel.
 */
sealed interface OutfitBuilderEvent {
    /** Emitted when the outfit was saved successfully. */
    data object SaveSuccess : OutfitBuilderEvent
    /** Emitted when the save operation failed. */
    data object SaveError : OutfitBuilderEvent
}

/**
 * ViewModel for the Outfit Builder screen.
 *
 * Manages the in-progress outfit name and the ordered list of selected clothing items.
 * Reads picker results deposited by [WardrobePickerScreen] via [SavedStateHandle]
 * under [PICKER_RESULT_KEY], then persists the finished outfit through [OutfitRepository].
 */
@HiltViewModel
class OutfitBuilderViewModel @Inject constructor(
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
    /** One-time [OutfitBuilderEvent] emissions for the screen to collect. */
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

    /** Consolidated UI state combining name, member list, and saving flag. */
    val uiState: StateFlow<OutfitBuilderUiState> = combine(
        _name, members, _isSaving
    ) { name, members, isSaving ->
        OutfitBuilderUiState(name = name, members = members, isSaving = isSaving)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = OutfitBuilderUiState()
    )

    /** Updates the outfit name as the user types. */
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

    /** Removes the item with [itemId] from the outfit member list. */
    fun removeMember(itemId: Long) {
        _memberIds.value = _memberIds.value.filter { it != itemId }
    }

    /**
     * Saves the current outfit via [OutfitRepository].
     * Uses the resolved [members] list (not raw IDs) so items deleted from the
     * wardrobe while the builder was open are already absent. Emits [OutfitBuilderEvent]
     * on completion.
     */
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

    /** Resolves a stored relative image [path] to a [File], or null if [path] is null. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }
}
