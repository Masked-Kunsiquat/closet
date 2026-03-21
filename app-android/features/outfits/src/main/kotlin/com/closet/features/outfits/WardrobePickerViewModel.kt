package com.closet.features.outfits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.SeasonEntity
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * UI state for the Wardrobe Picker screen.
 *
 * @property items Filtered clothing items visible in the list.
 * @property categories All categories available as filter chips.
 * @property seasons All seasons available as filter chips.
 * @property selectedCategoryId Active category filter, or null for no filter.
 * @property selectedSeasonId Active season filter, or null for no filter.
 * @property selectedItemIds IDs of items the user has tapped to include in the outfit.
 * @property isLoading True until the initial item list has loaded.
 */
data class WardrobePickerUiState(
    val items: List<ClothingItemDetail> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val seasons: List<SeasonEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val selectedSeasonId: Long? = null,
    val selectedItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

/**
 * ViewModel for the Wardrobe Picker screen.
 *
 * Applies optional category and season filters over the full wardrobe and tracks
 * which items the user has selected. Selections survive filter changes because
 * [selectedMembers] draws from the unfiltered item list.
 */
@HiltViewModel
class WardrobePickerViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }

    private val _categoryFilter = MutableStateFlow<Long?>(null)
    private val _seasonFilter = MutableStateFlow<Long?>(null)
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())

    // Hot source shared by both uiState and selectedMembers.
    private val _allItems: StateFlow<List<ClothingItemDetail>> = clothingRepository.getAllItemDetails()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _filters = combine(_categoryFilter, _seasonFilter) { cat, season -> cat to season }

    /** Filtered UI state combining items, lookups, active filters, and selection. */
    val uiState: StateFlow<WardrobePickerUiState> = combine(
        _allItems,
        lookupRepository.getCategories(),
        lookupRepository.getSeasons(),
        _filters,
        _selectedItemIds
    ) { allItems, categories, seasons, filters, selectedIds ->
        val (catId, seasonId) = filters
        val filtered = allItems
            .filter { catId == null || it.item.categoryId == catId }
            .filter { seasonId == null || it.seasons.any { s -> s.id == seasonId } }
        WardrobePickerUiState(
            items = filtered,
            categories = categories,
            seasons = seasons,
            selectedCategoryId = catId,
            selectedSeasonId = seasonId,
            selectedItemIds = selectedIds,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = WardrobePickerUiState()
    )

    /**
     * Selected items wrapped as [OutfitMember] with null layout (not yet placed on canvas).
     * Draws from the unfiltered item list so selections survive category/season filter changes.
     */
    val selectedMembers: StateFlow<List<OutfitMember>> = combine(
        _allItems,
        _selectedItemIds
    ) { allItems, selectedIds ->
        val allById = allItems.associateBy { it.item.id }
        selectedIds.mapNotNull { id -> allById[id]?.let { OutfitMember(item = it, layout = null) } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList()
    )

    /** Sets the active category filter to [id], or clears it when [id] is null. */
    fun selectCategory(id: Long?) {
        _categoryFilter.value = id
    }

    /** Sets the active season filter to [id], or clears it when [id] is null. */
    fun selectSeason(id: Long?) {
        _seasonFilter.value = id
    }

    /** Adds the item with [id] to the selection if not present, or removes it if already selected. */
    fun toggleItem(id: Long) {
        _selectedItemIds.value = _selectedItemIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    /** Resolves a stored relative image [path] to a [File], or null if [path] is null. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }
}
