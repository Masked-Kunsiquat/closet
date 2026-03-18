package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for the main Closet (Wardrobe) screen.
 * Responsible for providing the list of clothing items and available categories for filtering.
 * Parity: This is the native equivalent of hooks/useClothingItems.ts.
 */
@HiltViewModel
class ClosetViewModel @Inject constructor(
    private val clothingRepository: ClothingRepository,
    private val lookupRepository: LookupRepository
) : ViewModel() {

    companion object {
        private const val SHARED_SUBSCRIPTION_TIMEOUT_MS = 5000L
    }

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

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
     * The list of clothing items to display in the grid.
     * Items are provided with metadata like category names and wear counts.
     * Logic: Filters items based on the selected category if one is set.
     * Refined: Includes categories flow in combine to trigger recomputation on category updates.
     */
    val items: StateFlow<List<ClothingItemWithMeta>> = combine(
        clothingRepository.getAllItems(),
        _selectedCategoryId,
        categories
    ) { allItems, selectedId, categoriesList ->
        if (selectedId == null) {
            allItems
        } else {
            val selectedCategoryName = categoriesList.find { it.id == selectedId }?.name
            if (selectedCategoryName != null) {
                allItems.filter { it.categoryName == selectedCategoryName }
            } else {
                allItems
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SHARED_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList()
    )

    /**
     * Updates the currently selected category filter.
     * @param categoryId The ID of the category to filter by, or null to clear filter.
     */
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }
}
