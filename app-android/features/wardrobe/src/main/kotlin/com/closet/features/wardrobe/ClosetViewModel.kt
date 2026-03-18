package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    /**
     * The list of clothing items to display in the grid.
     * Items are provided with metadata like category names and wear counts.
     * Parity: Mirrored order (newest first) and includes wear_count.
     */
    val items: StateFlow<List<ClothingItemWithMeta>> = clothingRepository.getAllItems()
        .stateIn(
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
}
