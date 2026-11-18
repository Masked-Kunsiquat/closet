package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface BulkWashUiState {
    data object Loading : BulkWashUiState
    data object Error : BulkWashUiState
    data class Success(
        val items: List<ClothingItemWithMeta>,
        val selectedIds: Set<Long>,
        val applyError: Boolean = false,
    ) : BulkWashUiState
}

/**
 * ViewModel for the Bulk Wash / Laundry Day screen.
 *
 * Exposes all active wardrobe items alongside a selection set so the user can
 * mark multiple items as Clean or Dirty in one action. Triggered via the
 * "Laundry Day" app shortcut or the Settings screen.
 */
class BulkWashViewModel(
    private val clothingRepository: ClothingRepository,
    private val imagePathResolver: ImagePathResolver,
    private val shortcutUsageReporter: ShortcutUsageReporter,
) : ViewModel() {

    private val _selectedIds  = MutableStateFlow<Set<Long>>(emptySet())
    private val _applyError   = MutableStateFlow(false)
    private val applyMutex    = Mutex()

    val uiState: StateFlow<BulkWashUiState> = combine(
        clothingRepository.getAllItems(),
        _selectedIds,
        _applyError,
    ) { items, selected, applyError ->
        BulkWashUiState.Success(items, selected, applyError) as BulkWashUiState
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
     * Applies [status] to every selected item.
     * If all updates succeed, reports the shortcut used and clears the selection.
     * If any update fails, the selection is kept and [BulkWashUiState.Success.applyError]
     * is set to true so the UI can surface the failure.
     */
    fun applyWashStatus(status: WashStatus) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (!applyMutex.tryLock()) return@launch
            try {
                _applyError.value = false
                val results = ids.map { id -> async { clothingRepository.updateWashStatus(id, status) } }.awaitAll()
                if (results.any { it is DataResult.Error }) {
                    _applyError.value = true
                } else {
                    shortcutUsageReporter.reportUsed("laundry_day")
                    clearSelection()
                }
            } finally {
                applyMutex.unlock()
            }
        }
    }

    /** Dismisses the apply-error banner after the user has acknowledged it. */
    fun dismissApplyError() {
        _applyError.value = false
    }

    /** Resolves a relative image path to an absolute path for display with Coil. */
    fun resolveImagePath(path: String?): String? = path?.let { imagePathResolver.absolutePath(it) }
}
