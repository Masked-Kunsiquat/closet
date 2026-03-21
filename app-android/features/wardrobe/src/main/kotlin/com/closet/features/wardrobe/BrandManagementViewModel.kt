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

sealed interface BrandManagementDialog {
    data class ConfirmDelete(val brand: BrandEntity) : BrandManagementDialog
    data class BlockedDelete(val brand: BrandEntity, val itemCount: Int) : BrandManagementDialog
}

data class BrandManagementUiState(
    val brands: List<BrandEntity> = emptyList(),
    val dialog: BrandManagementDialog? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BrandManagementViewModel @Inject constructor(
    private val brandRepository: BrandRepository
) : ViewModel() {

    private val _dialog = MutableStateFlow<BrandManagementDialog?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BrandManagementUiState> = combine(
        brandRepository.getAllBrands(),
        _dialog,
        _isLoading,
        _errorMessage
    ) { brandsResult, dialog, loading, error ->
        when (brandsResult) {
            is DataResult.Loading -> BrandManagementUiState(
                dialog = dialog,
                isLoading = true,
                errorMessage = error
            )
            is DataResult.Success -> BrandManagementUiState(
                brands = brandsResult.data,
                dialog = dialog,
                isLoading = loading,
                errorMessage = error
            )
            is DataResult.Error -> BrandManagementUiState(
                dialog = dialog,
                isLoading = false,
                errorMessage = mapAppErrorToMessage(brandsResult.throwable)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrandManagementUiState()
    )

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

    fun dismissDialog() {
        _dialog.value = null
    }

    fun onErrorConsumed() {
        _errorMessage.value = null
    }

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
