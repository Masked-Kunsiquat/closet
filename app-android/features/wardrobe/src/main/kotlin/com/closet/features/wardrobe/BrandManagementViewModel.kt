package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.util.AppError
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dialog state shown on the Brand Management screen.
 * Either prompts for confirmation to delete an unused brand, or informs
 * the user that the brand is blocked because it is still assigned to items.
 */
sealed interface BrandManagementDialog {
    /** Confirmation prompt shown when the brand has zero clothing items. */
    data class ConfirmDelete(val brand: BrandEntity) : BrandManagementDialog
    /** Informational dialog shown when the brand is assigned to one or more items. */
    data class BlockedDelete(val brand: BrandEntity, val itemCount: Int) : BrandManagementDialog
}

/** UI state for the Brand Management screen. */
data class BrandManagementUiState(
    val brands: List<BrandEntity> = emptyList(),
    val dialog: BrandManagementDialog? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for the Brand Management screen.
 *
 * Exposes a single [uiState] derived from the live brand list combined with
 * transient loading/error/dialog state. All write operations dispatch to
 * [BrandRepository] and surface errors via [uiState].
 */
@HiltViewModel
class BrandManagementViewModel @Inject constructor(
    private val brandRepository: BrandRepository
) : ViewModel() {

    private val _brands = MutableStateFlow<List<BrandEntity>>(emptyList())
    private val _dialog = MutableStateFlow<BrandManagementDialog?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            brandRepository.getAllBrands().collect { result ->
                when (result) {
                    is DataResult.Loading -> _isLoading.value = true
                    is DataResult.Success -> {
                        _isLoading.value = false
                        _brands.value = result.data
                    }
                    is DataResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = mapAppErrorToMessage(result.throwable)
                    }
                }
            }
        }
    }

    val uiState: StateFlow<BrandManagementUiState> = combine(
        _brands,
        _dialog,
        _isLoading,
        _errorMessage
    ) { brands, dialog, loading, error ->
        BrandManagementUiState(
            brands = brands,
            dialog = dialog,
            isLoading = loading,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrandManagementUiState()
    )

    /**
     * Initiates a delete request for [brand].
     * Shows [BrandManagementDialog.ConfirmDelete] if the brand is unused, or
     * [BrandManagementDialog.BlockedDelete] if clothing items still reference it.
     */
    fun requestDelete(brand: BrandEntity) {
        viewModelScope.launch {
            when (val result = brandRepository.getItemCountForBrand(brand.id)) {
                is DataResult.Success -> {
                    _dialog.value = if (result.data > 0) {
                        BrandManagementDialog.BlockedDelete(brand, result.data)
                    } else {
                        BrandManagementDialog.ConfirmDelete(brand)
                    }
                }
                is DataResult.Error -> _errorMessage.value = mapAppErrorToMessage(result.throwable)
                else -> Unit
            }
        }
    }

    /** Executes the delete for the brand with [id] and dismisses the dialog on completion. */
    fun confirmDelete(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = brandRepository.deleteBrand(id)) {
                    is DataResult.Success -> dismissDialog()
                    is DataResult.Error -> {
                        _errorMessage.value = mapAppErrorToMessage(result.throwable)
                        dismissDialog()
                    }
                    else -> Unit
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Inserts a new brand when [id] is null, or renames the existing brand when [id] is non-null.
     * Blank names are silently ignored.
     */
    fun saveBrand(id: Long?, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = if (id == null) {
                    brandRepository.insertBrand(trimmed)
                } else {
                    brandRepository.updateBrand(id, trimmed)
                }
                if (result is DataResult.Error) {
                    _errorMessage.value = mapAppErrorToMessage(result.throwable)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Clears the currently shown dialog. */
    fun dismissDialog() {
        _dialog.value = null
    }

    /** Clears the transient error message once the UI has consumed it. */
    fun onErrorConsumed() {
        _errorMessage.value = null
    }

    /** Maps an [AppError] (or any throwable) to a human-readable message for the UI. */
    private fun mapAppErrorToMessage(throwable: Throwable): String = when (throwable) {
        is AppError.DatabaseError.ConstraintViolation -> "A brand with that name already exists."
        is AppError.DatabaseError.NotFound -> "Brand not found."
        is AppError.DatabaseError.QueryError -> "A database error occurred."
        is AppError.ValidationError.InvalidInput -> throwable.message ?: "Invalid input."
        is AppError.ValidationError.MissingField -> "Missing required field: ${throwable.fieldName}."
        is AppError.Unexpected -> "An unexpected error occurred."
        else -> "Something went wrong."
    }
}
