package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the main Closet (Wardrobe) screen.
 * Responsible for providing the list of clothing items and available categories for filtering.
 * Parity: This is the native equivalent of hooks/useClothingItems.ts.
 */
@HiltViewModel
class ClosetViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    companion object {
        private const val SHARED_SUBSCRIPTION_TIMEOUT_MS = 5000L
    }

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _selectedColorIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedColorIds: StateFlow<Set<Long>> = _selectedColorIds.asStateFlow()

    /**
     * Available categories for filtering items in the main grid.
     */
    val categories: StateFlow<List<CategoryEntity>> = lookupRepository.getCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SHARED_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * Available colors for filtering items.
     */
    val allColors: StateFlow<List<ColorEntity>> = lookupRepository.getColors()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SHARED_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * The list of clothing items to display in the grid.
     * Items are provided with full metadata (ClothingItemDetail) for future filtering.
     */
    val items: StateFlow<List<ClothingItemDetail>> = combine(
        clothingRepository.getAllItemDetails(),
        _selectedCategoryId,
        _selectedColorIds
    ) { allItems, selectedCategoryId, selectedColorIds ->
        allItems.filter { item ->
            val matchesCategory = selectedCategoryId == null || item.item.categoryId == selectedCategoryId
            val matchesColors = selectedColorIds.isEmpty() || item.colors.any { it.id in selectedColorIds }
            matchesCategory && matchesColors
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SHARED_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList()
    )

    /**
     * Resolves a relative image path to an absolute File for UI display.
     */
    fun resolveImagePath(path: String?): File? {
        return path?.let { storageRepository.getFile(it) }
    }

    /**
     * Updates the currently selected category filter.
     * @param categoryId The ID of the category to filter by, or null to clear filter.
     */
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * Toggles the selection of a color filter.
     * @param colorId The ID of the color to toggle.
     */
    fun toggleColor(colorId: Long) {
        _selectedColorIds.value = _selectedColorIds.value.let { current ->
            if (current.contains(colorId)) current - colorId else current + colorId
        }
    }

    /**
     * Clears all selected color filters.
     */
    fun clearColors() {
        _selectedColorIds.value = emptySet()
    }
}
