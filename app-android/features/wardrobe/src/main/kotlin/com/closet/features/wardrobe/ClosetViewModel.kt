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
     * The list of clothing items to display in the grid.
     * Items are provided with metadata like category names and wear counts.
     * Parity: Mirrored order (newest first) and includes wear_count.
     * Logic: Filters items based on the selected category if one is set.
     */
    val items: StateFlow<List<ClothingItemWithMeta>> = combine(
        clothingRepository.getAllItems(),
        _selectedCategoryId
    ) { allItems, selectedId ->
        if (selectedId == null) {
            allItems
        } else {
            // Note: In a larger DB, we'd move this filter to the DAO/Repository.
            // For Closet's local-first scale, in-memory filtering is performant.
            // We match by categoryName since ClothingItemWithMeta doesn't expose categoryId directly yet,
            // but for robustness we should ideally use IDs if we update the model.
            // Checking the category name from the entity list:
            val selectedCategoryName = categories.value.find { it.id == selectedId }?.name
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
     * Available categories for filtering items in the main grid.
     */
    val categories: StateFlow<List<CategoryEntity>> = lookupRepository.getCategories()
        .stateIn(
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
