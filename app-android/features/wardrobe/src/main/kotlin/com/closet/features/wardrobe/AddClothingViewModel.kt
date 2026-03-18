package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * UI state for the Add Clothing screen.
 */
data class AddClothingUiState(
    val name: String = "",
    val brand: String = "",
    val isNameError: Boolean = false,
    val canSave: Boolean = false
)

/**
 * ViewModel for managing the state of the Add Clothing form.
 * Implements Unidirectional Data Flow (UDF) for form updates.
 */
@HiltViewModel
class AddClothingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AddClothingUiState())
    val uiState: StateFlow<AddClothingUiState> = _uiState.asStateFlow()

    /**
     * Updates the clothing name and performs basic validation.
     */
    fun updateName(newName: String) {
        _uiState.update { it.copy(
            name = newName,
            isNameError = false, // Reset error while typing
            canSave = newName.isNotBlank()
        ) }
    }

    /**
     * Updates the clothing brand.
     */
    fun updateBrand(newBrand: String) {
        _uiState.update { it.copy(brand = newBrand) }
    }

    /**
     * Validates the form. Returns true if valid.
     */
    fun validate(): Boolean {
        val isValid = _uiState.value.name.isNotBlank()
        _uiState.update { it.copy(isNameError = !isValid) }
        return isValid
    }
}
