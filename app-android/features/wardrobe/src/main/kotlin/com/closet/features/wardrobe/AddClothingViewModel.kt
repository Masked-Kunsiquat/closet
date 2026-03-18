package com.closet.features.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.repository.LookupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * UI state for the Add Clothing screen.
 */
data class AddClothingUiState(
    val name: String = "",
    val brand: String = "",
    val category: CategoryEntity? = null,
    val subcategory: SubcategoryEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val isNameError: Boolean = false,
    val canSave: Boolean = false
)

/**
 * ViewModel for managing the state of the Add Clothing form.
 * Implements Unidirectional Data Flow (UDF) for form updates.
 */
@HiltViewModel
class AddClothingViewModel @Inject constructor(
    private val lookupRepository: LookupRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    private val _brand = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    private val _selectedSubcategory = MutableStateFlow<SubcategoryEntity?>(null)
    private val _isNameError = MutableStateFlow(false)

    val categories: StateFlow<List<CategoryEntity>> = lookupRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subcategories: StateFlow<List<SubcategoryEntity>> = _selectedCategory
        .flatMapLatest { category ->
            if (category == null) flowOf(emptyList())
            else lookupRepository.getSubcategories(category.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AddClothingUiState> = combine(
        _name, _brand, _selectedCategory, _selectedSubcategory, _isNameError, categories, subcategories
    ) { args: Array<Any?> ->
        AddClothingUiState(
            name = args[0] as String,
            brand = args[1] as String,
            category = args[2] as CategoryEntity?,
            subcategory = args[3] as SubcategoryEntity?,
            isNameError = args[4] as Boolean,
            categories = args[5] as List<CategoryEntity>,
            subcategories = args[6] as List<SubcategoryEntity>,
            canSave = (args[0] as String).isNotBlank()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddClothingUiState()
    )

    /**
     * Updates the clothing name and performs basic validation.
     */
    fun updateName(newName: String) {
        _name.value = newName
        _isNameError.value = false
    }

    /**
     * Updates the clothing brand.
     */
    fun updateBrand(newBrand: String) {
        _brand.value = newBrand
    }

    /**
     * Selects a category and resets the subcategory.
     */
    fun selectCategory(category: CategoryEntity?) {
        _selectedCategory.value = category
        _selectedSubcategory.value = null
    }

    /**
     * Selects a subcategory.
     */
    fun selectSubcategory(subcategory: SubcategoryEntity?) {
        _selectedSubcategory.value = subcategory
    }

    /**
     * Validates the form. Returns true if valid.
     */
    fun validate(): Boolean {
        val isValid = _name.value.isNotBlank()
        _isNameError.value = !isValid
        return isValid
    }
}
