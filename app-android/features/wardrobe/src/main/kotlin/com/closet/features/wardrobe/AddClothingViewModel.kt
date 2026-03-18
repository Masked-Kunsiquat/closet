package com.closet.features.wardrobe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI state for the Add Clothing screen.
 */
data class AddClothingUiState(
    val name: String = "",
    val brand: String = "",
    val category: CategoryEntity? = null,
    val subcategory: SubcategoryEntity? = null,
    val price: String = "",
    val purchaseDate: LocalDate? = null,
    val purchaseLocation: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    val imageFile: File? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val isNameError: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: Int? = null,
    val canSave: Boolean = false,
    val isDirty: Boolean = false
) {
    val formattedDate: String? = purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/**
 * ViewModel for managing the state of the Add Clothing form.
 * Implements Unidirectional Data Flow (UDF) for form updates.
 */
@HiltViewModel
class AddClothingViewModel @Inject constructor(
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    private val _brand = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    private val _selectedSubcategory = MutableStateFlow<SubcategoryEntity?>(null)
    private val _price = MutableStateFlow("")
    private val _purchaseDate = MutableStateFlow<LocalDate?>(null)
    private val _purchaseLocation = MutableStateFlow("")
    private val _notes = MutableStateFlow("")
    private val _imagePath = MutableStateFlow<String?>(null)
    
    private val _isNameError = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<Int?>(null)

    // One-time events for navigation
    private val _events = Channel<AddClothingEvent>()
    val events = _events.receiveAsFlow()

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
        _name, _brand, _selectedCategory, _selectedSubcategory, 
        _price, _purchaseDate, _purchaseLocation, _notes,
        _imagePath, _isNameError, _isSaving, _errorMessage, 
        categories, subcategories
    ) { args: Array<Any?> ->
        val name = args[0] as String
        val brand = args[1] as String
        val category = args[2] as CategoryEntity?
        val subcategory = args[3] as SubcategoryEntity?
        val price = args[4] as String
        val purchaseDate = args[5] as LocalDate?
        val purchaseLocation = args[6] as String
        val notes = args[7] as String
        val imagePath = args[8] as String?
        
        val isDirty = name.isNotBlank() || brand.isNotBlank() || category != null || 
                      price.isNotBlank() || purchaseDate != null || purchaseLocation.isNotBlank() ||
                      notes.isNotBlank() || imagePath != null

        AddClothingUiState(
            name = name,
            brand = brand,
            category = category,
            subcategory = subcategory,
            price = price,
            purchaseDate = purchaseDate,
            purchaseLocation = purchaseLocation,
            notes = notes,
            imagePath = imagePath,
            imageFile = imagePath?.let { storageRepository.getFile(it) },
            isNameError = args[9] as Boolean,
            isSaving = args[10] as Boolean,
            errorMessage = args[11] as Int?,
            categories = args[12] as List<CategoryEntity>,
            subcategories = args[13] as List<SubcategoryEntity>,
            canSave = name.isNotBlank() && !(args[10] as Boolean),
            isDirty = isDirty
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddClothingUiState()
    )

    fun updateName(newName: String) {
        _name.value = newName
        _isNameError.value = false
    }

    fun updateBrand(newBrand: String) {
        _brand.value = newBrand
    }

    fun selectCategory(category: CategoryEntity?) {
        _selectedCategory.value = category
        _selectedSubcategory.value = null
    }

    fun selectSubcategory(subcategory: SubcategoryEntity?) {
        _selectedSubcategory.value = subcategory
    }

    fun updatePrice(newPrice: String) {
        // Simple validation to allow only numbers and decimal point
        if (newPrice.isEmpty() || newPrice.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
            _price.value = newPrice
        }
    }

    fun updatePurchaseDate(date: LocalDate?) {
        _purchaseDate.value = date
    }

    fun updatePurchaseLocation(location: String) {
        _purchaseLocation.value = location
    }

    fun updateNotes(newNotes: String) {
        _notes.value = newNotes
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                _imagePath.value?.let { storageRepository.deleteImage(it) }
                val relativePath = storageRepository.saveImage(uri)
                _imagePath.value = relativePath
            } catch (e: Exception) {
                _errorMessage.value = R.string.wardrobe_error_save_failed
            }
        }
    }

    fun save() {
        if (_name.value.isBlank()) {
            _isNameError.value = true
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null

            val item = ClothingItemEntity(
                name = _name.value.trim(),
                brand = _brand.value.trim().takeIf { it.isNotBlank() },
                categoryId = _selectedCategory.value?.id,
                subcategoryId = _selectedSubcategory.value?.id,
                purchasePrice = _price.value.toDoubleOrNull(),
                purchaseDate = _purchaseDate.value?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                purchaseLocation = _purchaseLocation.value.trim().takeIf { it.isNotBlank() },
                notes = _notes.value.trim().takeIf { it.isNotBlank() },
                imagePath = _imagePath.value
            )

            when (val result = clothingRepository.insertItem(item)) {
                is DataResult.Success -> {
                    _events.send(AddClothingEvent.NavigateBack)
                }
                is DataResult.Error -> {
                    _errorMessage.value = R.string.wardrobe_error_save_failed
                }
                else -> { /* No-op */ }
            }
            _isSaving.value = false
        }
    }
}
