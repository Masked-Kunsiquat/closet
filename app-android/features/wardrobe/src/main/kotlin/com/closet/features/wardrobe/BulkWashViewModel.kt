package com.closet.features.wardrobe

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface BulkWashUiState {
    data object Loading : BulkWashUiState
    data object Error : BulkWashUiState
    data class Success(
        val items: List<ClothingItemWithMeta>,
        val selectedIds: Set<Long>,
    ) : BulkWashUiState
}

/**
 * ViewModel for the Bulk Wash / Laundry Day screen.
 *
 * Exposes all active wardrobe items alongside a selection set so the user can
 * mark multiple items as Clean or Dirty in one action. Triggered via the
 * "Laundry Day" app shortcut or the Settings screen.
 */
@HiltViewModel
class BulkWashViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val clothingRepository: ClothingRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<BulkWashUiState> = clothingRepository.getAllItems()
        .combine(_selectedIds) { items, selected ->
            BulkWashUiState.Success(items, selected) as BulkWashUiState
        }
        .catch { emit(BulkWashUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BulkWashUiState.Loading,
        )

    fun toggleSelection(id: Long) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAll(itemIds: List<Long>) {
        _selectedIds.value = itemIds.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Applies [status] to every selected item, then clears the selection.
     * Each update is best-effort — failures are silently swallowed so one
     * bad row doesn't abort the rest of the batch.
     */
    fun applyWashStatus(status: WashStatus) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> clothingRepository.updateWashStatus(id, status) }
            ShortcutManagerCompat.reportShortcutUsed(appContext, "laundry_day")
            clearSelection()
        }
    }

    /** Resolves a relative image path to an absolute [File] for display with Coil. */
    fun resolveImagePath(path: String?): File? = path?.let { storageRepository.getFile(it) }
}
