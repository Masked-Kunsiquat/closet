package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.OccasionEntity
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
 * Snapshot of all state needed to render the Closet screen.
 *
 * Filter lookup lists ([colors], [seasons], [occasions]) are pre-loaded so the
 * FilterPanel can build its chips without an extra repository call.
 *
 * [activeFilterCount] is the number of active filter *dimensions* (max 4:
 * colors, seasons, occasions, favorites). Use this to drive the badge on the
 * filter icon button.
 */
data class ClosetUiState(
    val items: List<ClothingItemDetail> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val colors: List<ColorEntity> = emptyList(),
    val seasons: List<SeasonEntity> = emptyList(),
    val occasions: List<OccasionEntity> = emptyList(),
    val selectedColorIds: Set<Long> = emptySet(),
    val selectedSeasonIds: Set<Long> = emptySet(),
    val selectedOccasionIds: Set<Long> = emptySet(),
    val favoritesOnly: Boolean = false,
    val activeFilterCount: Int = 0,
)

/**
 * Packs the four advanced filter selections into a single value so they can
 * occupy one slot in the outer [combine] (which is limited to 5 flows).
 */
private data class FilterSelections(
    val colorIds: Set<Long>,
    val seasonIds: Set<Long>,
    val occIds: Set<Long>,
    val favOnly: Boolean,
) {
    /** Number of active filter dimensions (max 4). Drives the filter badge. */
    val activeCount: Int get() = listOf(
        colorIds.isNotEmpty(),
        seasonIds.isNotEmpty(),
        occIds.isNotEmpty(),
        favOnly
    ).count { it }
}

/**
 * ViewModel for the main Closet (Wardrobe) screen.
 *
 * Exposes a single [uiState] built from [combine] over the item list, the five
 * filter flows, and lookup data. Changing any filter flow cancels the previous
 * emission and rebuilds the filtered list reactively.
 *
 * Filtering is in-memory: [ClothingRepository.getAllItemDetails] is subscribed
 * once and items are filtered by predicate inside the combine block.
 *
 * Parity: native equivalent of hooks/useClothingItems.ts + FilterPanel state.
 */
@HiltViewModel
class ClosetViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _selectedCategoryId  = MutableStateFlow<Long?>(null)
    private val _selectedColorIds    = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedSeasonIds   = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedOccasionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _favoritesOnly       = MutableStateFlow(false)

    private val filterSelections = combine(
        _selectedColorIds,
        _selectedSeasonIds,
        _selectedOccasionIds,
        _favoritesOnly
    ) { colorIds, seasonIds, occIds, favOnly ->
        FilterSelections(colorIds, seasonIds, occIds, favOnly)
    }

    /** Aggregated UI state for the Closet screen. */
    val uiState: StateFlow<ClosetUiState> = combine(
        clothingRepository.getAllItemDetails(),
        _selectedCategoryId,
        lookupRepository.getCategories(),
        combine(
            lookupRepository.getColors(),
            lookupRepository.getSeasons(),
            lookupRepository.getOccasions()
        ) { colors, seasons, occasions -> Triple(colors, seasons, occasions) },
        filterSelections
    ) { allItems, categoryId, categories, (colors, seasons, occasions), fs ->
        val filtered = allItems
            .filter { categoryId == null || it.item.categoryId == categoryId }
            .filter { fs.colorIds.isEmpty() || it.colors.any { c -> c.id in fs.colorIds } }
            .filter { fs.seasonIds.isEmpty() || it.seasons.any { s -> s.id in fs.seasonIds } }
            .filter { fs.occIds.isEmpty() || it.occasions.any { o -> o.id in fs.occIds } }
            .filter { !fs.favOnly || it.item.isFavorite == 1 }
        ClosetUiState(
            items = filtered,
            categories = categories,
            selectedCategoryId = categoryId,
            colors = colors,
            seasons = seasons,
            occasions = occasions,
            selectedColorIds = fs.colorIds,
            selectedSeasonIds = fs.seasonIds,
            selectedOccasionIds = fs.occIds,
            favoritesOnly = fs.favOnly,
            activeFilterCount = fs.activeCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClosetUiState()
    )

    /** Resolves a relative image path to an absolute [File] for UI display. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }

    /** Updates the active category filter. Pass null to show all items. */
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /** Adds [id] to the color filter if absent; removes it if already selected. */
    fun toggleColorFilter(id: Long) {
        _selectedColorIds.value = _selectedColorIds.value.toggle(id)
    }

    /** Adds [id] to the season filter if absent; removes it if already selected. */
    fun toggleSeasonFilter(id: Long) {
        _selectedSeasonIds.value = _selectedSeasonIds.value.toggle(id)
    }

    /** Adds [id] to the occasion filter if absent; removes it if already selected. */
    fun toggleOccasionFilter(id: Long) {
        _selectedOccasionIds.value = _selectedOccasionIds.value.toggle(id)
    }

    /** Toggles the favorites-only filter. */
    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    /** Clears all active filters, including category. */
    fun clearAllFilters() {
        _selectedCategoryId.value = null
        _selectedColorIds.value = emptySet()
        _selectedSeasonIds.value = emptySet()
        _selectedOccasionIds.value = emptySet()
        _favoritesOnly.value = false
    }

    /**
     * Clears only the advanced filter selections (color, season, occasion).
     * Does not affect category or favorites — used by the FilterPanel "Clear all" button.
     */
    fun clearAdvancedFilters() {
        _selectedColorIds.value = emptySet()
        _selectedSeasonIds.value = emptySet()
        _selectedOccasionIds.value = emptySet()
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
