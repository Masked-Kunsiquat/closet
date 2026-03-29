package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.OutfitItem
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI state for the Outfit Builder screen.
 *
 * @property name Optional outfit name entered by the user.
 * @property members Clothing items currently added to the outfit, ordered by selection time.
 * @property isSaving True while the save operation is in flight.
 * @property isEditing True when editing an existing outfit (vs creating a new one).
 * @property canSave Derived flag — true when there is at least one member and no save is pending.
 */
data class OutfitBuilderUiState(
    val name: String = "",
    val members: List<OutfitMember> = emptyList(),
    val isSaving: Boolean = false,
    val isEditing: Boolean = false
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
 * When [SavedStateHandle] carries an [outfitId] ≥ 0, the ViewModel operates in edit mode:
 * it loads the existing outfit on init and calls [OutfitRepository.updateOutfit] on save.
 */
@HiltViewModel
class OutfitBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothingRepository: ClothingRepository,
    private val outfitRepository: OutfitRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        private const val NO_OUTFIT = -1L
    }

    // -1 means "new outfit"; any positive value means edit mode.
    private val editOutfitId: Long = savedStateHandle.get<Long>("outfitId") ?: NO_OUTFIT

    private val _name = MutableStateFlow("")
    private val _notes = MutableStateFlow("")
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

    /** Consolidated UI state combining name, member list, saving flag, and edit mode. */
    val uiState: StateFlow<OutfitBuilderUiState> = combine(
        _name, members, _isSaving
    ) { name, members, isSaving ->
        OutfitBuilderUiState(
            name = name,
            members = members,
            isSaving = isSaving,
            isEditing = editOutfitId != NO_OUTFIT
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = OutfitBuilderUiState(isEditing = editOutfitId != NO_OUTFIT)
    )

    init {
        if (editOutfitId != NO_OUTFIT) {
            viewModelScope.launch {
                outfitRepository.getOutfitById(editOutfitId).first()?.let { outfit ->
                    _name.value = outfit.name ?: ""
                    _notes.value = outfit.notes ?: ""
                    _memberIds.value = outfit.items
                        .sortedBy { it.zIndex }
                        .map { it.clothingItem.id }
                }
            }
        }
    }

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
     * wardrobe while the builder was open are already absent.
     * Creates a new outfit when [editOutfitId] is -1; updates the existing outfit otherwise.
     */
    fun save() {
        val resolvedMembers = members.value
        if (_isSaving.value || resolvedMembers.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            val name = _name.value.trim().ifEmpty { null }
            val result = if (editOutfitId == NO_OUTFIT) {
                outfitRepository.createOutfit(
                    name = name,
                    notes = null,
                    itemIds = resolvedMembers.map { it.item.item.id }
                )
            } else {
                val outfitItems = resolvedMembers.mapIndexed { index, member ->
                    OutfitItem(
                        clothingItem = member.item.item,
                        posX = member.layout?.posX,
                        posY = member.layout?.posY,
                        scale = member.layout?.scale,
                        zIndex = member.layout?.zIndex ?: index
                    )
                }
                outfitRepository.updateOutfit(
                    outfitId = editOutfitId,
                    name = name,
                    notes = _notes.value.trim().ifEmpty { null },
                    items = outfitItems
                )
            }
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
