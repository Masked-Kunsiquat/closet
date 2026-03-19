package com.closet.features.wardrobe

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.model.WashStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI state for the Clothing Form (Add/Edit).
 */
data class ClothingFormUiState(
    val isEditMode: Boolean = false,
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
    val isLoading: Boolean = false,
    val errorMessage: Int? = null,
    val canSave: Boolean = false,
    val isDirty: Boolean = false
) {
    val formattedDate: String? = purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/**
 * ViewModel for managing the state of the Clothing form (Add or Edit).
 * Implements Hydration logic for editing existing items.
 */
@HiltViewModel
class ClothingFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lookupRepository: LookupRepository,
    private val storageRepository: StorageRepository,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val editDestination = try {
        savedStateHandle.toRoute<EditClothingDestination>()
    } catch (_: Exception) {
        null
    }

    private val itemId: Long? = editDestination?.itemId
    private val isEditMode = itemId != null

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
    private val _isLoading = MutableStateFlow(isEditMode)
    private val _errorMessage = MutableStateFlow<Int?>(null)
    
    // To handle image replacement cleanup
    private var originalImagePath: String? = null
    private var originalEntity: ClothingItemEntity? = null

    // One-time events for navigation
    private val _events = Channel<ClothingFormEvent>()
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

    val uiState: StateFlow<ClothingFormUiState> = combine(
        listOf(_name, _brand, _selectedCategory, _selectedSubcategory, 
        _price, _purchaseDate, _purchaseLocation, _notes,
        _imagePath, _isNameError, _isSaving, _isLoading, _errorMessage, 
        categories, subcategories)
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

        val isDirty = if (isEditMode && originalEntity != null) {
            val e = originalEntity!!
            name != e.name || 
            brand != (e.brand ?: "") || 
            category?.id != e.categoryId || 
            subcategory?.id != e.subcategoryId ||
            price != (e.purchasePrice?.toString() ?: "") ||
            purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) != e.purchaseDate ||
            purchaseLocation != (e.purchaseLocation ?: "") ||
            notes != (e.notes ?: "") ||
            imagePath != e.imagePath
        } else {
            name.isNotBlank() || brand.isNotBlank() || category != null || 
            price.isNotBlank() || purchaseDate != null || purchaseLocation.isNotBlank() ||
            notes.isNotBlank() || imagePath != null
        }

        @Suppress("UNCHECKED_CAST")
        ClothingFormUiState(
            isEditMode = isEditMode,
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
            isLoading = args[11] as Boolean,
            errorMessage = args[12] as Int?,
            categories = args[13] as List<CategoryEntity>,
            subcategories = args[14] as List<SubcategoryEntity>,
            canSave = name.isNotBlank() && !(args[10] as Boolean),
            isDirty = isDirty
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClothingFormUiState(isEditMode = isEditMode, isLoading = isEditMode)
    )

    init {
        if (isEditMode && itemId != null) {
            loadItemForEditing(itemId)
        }
    }

    private fun loadItemForEditing(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val entityResult = clothingRepository.getItemEntityById(id)
            
            if (entityResult is DataResult.Success) {
                val entity = entityResult.data
                originalEntity = entity
                originalImagePath = entity.imagePath
                
                // Hydrate the fields
                _name.value = entity.name
                _brand.value = entity.brand ?: ""
                _price.value = entity.purchasePrice?.toString() ?: ""
                _purchaseDate.value = entity.purchaseDate?.let { try { LocalDate.parse(it) } catch(_: Exception) { null } }
                _purchaseLocation.value = entity.purchaseLocation ?: ""
                _notes.value = entity.notes ?: ""
                _imagePath.value = entity.imagePath
                
                // Fetch Category and Subcategory entities for the selectors
                val catId = entity.categoryId
                if (catId != null) {
                    val cats = lookupRepository.getCategories().first()
                    val selectedCat = cats.find { it.id == catId }
                    _selectedCategory.value = selectedCat
                    
                    val subId = entity.subcategoryId
                    if (subId != null) {
                        val subcats = lookupRepository.getSubcategories(catId).first()
                        _selectedSubcategory.value = subcats.find { it.id == subId }
                    }
                }
            } else {
                _errorMessage.value = R.string.wardrobe_error_load_failed
            }
            _isLoading.value = false
        }
    }

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
                if (_imagePath.value != originalImagePath) {
                    _imagePath.value?.let { storageRepository.deleteImage(it) }
                }
                val relativePath = storageRepository.saveImage(uri)
                _imagePath.value = relativePath
            } catch (_: Exception) {
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
                id = itemId ?: 0,
                name = _name.value.trim(),
                brand = _brand.value.trim().takeIf { it.isNotBlank() },
                categoryId = _selectedCategory.value?.id,
                subcategoryId = _selectedSubcategory.value?.id,
                purchasePrice = _price.value.toDoubleOrNull(),
                purchaseDate = _purchaseDate.value?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                purchaseLocation = _purchaseLocation.value.trim().takeIf { it.isNotBlank() },
                notes = _notes.value.trim().takeIf { it.isNotBlank() },
                imagePath = _imagePath.value,
                status = originalEntity?.status ?: ClothingStatus.Active,
                washStatus = originalEntity?.washStatus ?: WashStatus.Clean,
                isFavorite = originalEntity?.isFavorite ?: 0,
                createdAt = originalEntity?.createdAt ?: Instant.now(),
                updatedAt = Instant.now()
            )

            val result = if (isEditMode) {
                clothingRepository.updateItem(item)
            } else {
                clothingRepository.insertItem(item)
            }

            when (result) {
                is DataResult.Success -> {
                    if (isEditMode && _imagePath.value != originalImagePath) {
                        originalImagePath?.let { storageRepository.deleteImage(it) }
                    }
                    _events.send(ClothingFormEvent.NavigateBack)
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

sealed class ClothingFormEvent {
    object NavigateBack : ClothingFormEvent()
}
