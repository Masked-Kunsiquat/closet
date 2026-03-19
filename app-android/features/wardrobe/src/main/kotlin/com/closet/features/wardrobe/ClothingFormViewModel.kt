package com.closet.features.wardrobe

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.palette.graphics.Palette
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.ColorMatcher
import com.closet.core.data.util.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val selectedColors: List<ColorEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val allColors: List<ColorEntity> = emptyList(),
    val isNameError: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: Int? = null,
    val canSave: Boolean = false,
    val isDirty: Boolean = false
) {
    val formattedDate: String? = purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private data class FormBasic(
    val name: String,
    val brand: String,
    val category: CategoryEntity?,
    val subcategory: SubcategoryEntity?,
    val price: String
)

private data class FormDetails(
    val purchaseDate: LocalDate?,
    val purchaseLocation: String,
    val notes: String,
    val imagePath: String?,
    val selectedColors: List<ColorEntity>
)

private data class FormStatus(
    val isNameError: Boolean,
    val isSaving: Boolean,
    val isLoading: Boolean,
    val errorMessage: Int?
)

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
    private val _selectedColors = MutableStateFlow<List<ColorEntity>>(emptyList())
    
    private val _isNameError = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(isEditMode)
    private val _errorMessage = MutableStateFlow<Int?>(null)
    
    // To handle image replacement cleanup
    private var originalImagePath: String? = null
    private var originalEntity: ClothingItemEntity? = null
    private var originalColors: List<ColorEntity> = emptyList()

    // One-time events for navigation
    private val _events = Channel<ClothingFormEvent>()
    val events = _events.receiveAsFlow()

    val categories: StateFlow<List<CategoryEntity>> = lookupRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allColors: StateFlow<List<ColorEntity>> = lookupRepository.getColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subcategories: StateFlow<List<SubcategoryEntity>> = _selectedCategory
        .flatMapLatest { category ->
            if (category == null) flowOf(emptyList())
            else lookupRepository.getSubcategories(category.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val basicFields = combine(
        _name, _brand, _selectedCategory, _selectedSubcategory, _price
    ) { name, brand, category, subcategory, price ->
        FormBasic(name, brand, category, subcategory, price)
    }

    private val detailFields = combine(
        _purchaseDate, _purchaseLocation, _notes, _imagePath, _selectedColors
    ) { date, location, notes, path, colors ->
        FormDetails(date, location, notes, path, colors)
    }

    private val statusFields = combine(
        _isNameError, _isSaving, _isLoading, _errorMessage
    ) { nameError, saving, loading, error ->
        FormStatus(nameError, saving, loading, error)
    }

    val uiState: StateFlow<ClothingFormUiState> = combine(
        combine(basicFields, detailFields, statusFields) { b, d, s -> Triple(b, d, s) },
        categories,
        subcategories,
        allColors
    ) { (basic, details, status), cats, subcats, colors ->
        val isDirty = if (isEditMode && originalEntity != null) {
            val e = originalEntity!!
            val colorsChanged = details.selectedColors.size != originalColors.size ||
                    !details.selectedColors.containsAll(originalColors)

            basic.name != e.name || 
            basic.brand != (e.brand ?: "") || 
            basic.category?.id != e.categoryId || 
            basic.subcategory?.id != e.subcategoryId ||
            basic.price != (e.purchasePrice?.toString() ?: "") ||
            details.purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) != e.purchaseDate ||
            details.purchaseLocation != (e.purchaseLocation ?: "") ||
            details.notes != (e.notes ?: "") ||
            details.imagePath != e.imagePath ||
            colorsChanged
        } else {
            basic.name.isNotBlank() || basic.brand.isNotBlank() || basic.category != null || 
            basic.price.isNotBlank() || details.purchaseDate != null || details.purchaseLocation.isNotBlank() ||
            details.notes.isNotBlank() || details.imagePath != null || details.selectedColors.isNotEmpty()
        }

        ClothingFormUiState(
            isEditMode = isEditMode,
            name = basic.name,
            brand = basic.brand,
            category = basic.category,
            subcategory = basic.subcategory,
            price = basic.price,
            purchaseDate = details.purchaseDate,
            purchaseLocation = details.purchaseLocation,
            notes = details.notes,
            imagePath = details.imagePath,
            imageFile = details.imagePath?.let { storageRepository.getFile(it) },
            selectedColors = details.selectedColors,
            isNameError = status.isNameError,
            isSaving = status.isSaving,
            isLoading = status.isLoading,
            errorMessage = status.errorMessage,
            categories = cats,
            subcategories = subcats,
            allColors = colors,
            canSave = basic.name.isNotBlank() && !status.isSaving,
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

                // Hydrate colors
                val colors = clothingRepository.getItemColors(id).first()
                originalColors = colors
                _selectedColors.value = colors
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
                
                // Process colors from the new image
                extractColors(uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorMessage.value = R.string.wardrobe_error_image_failed
            }
        }
    }

    private suspend fun extractColors(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val bitmap = storageRepository.getBitmap(uri) ?: return@withContext
            val palette = Palette.from(bitmap).generate()
            
            val swatches = listOfNotNull(
                palette.vibrantSwatch,
                palette.mutedSwatch,
                palette.dominantSwatch
            )
            
            if (swatches.isNotEmpty()) {
                val availableColors = lookupRepository.getColors().first()
                if (availableColors.isNotEmpty()) {
                    val snappedColors = swatches.map { swatch ->
                        ColorMatcher.findNearestColor(swatch.rgb, availableColors)
                    }.distinctBy { it.id }
                    
                    _selectedColors.value = snappedColors
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Silently fail color extraction
        }
    }

    fun updateColors(colorIds: List<Long>) {
        viewModelScope.launch {
            val colors = lookupRepository.getColors().first()
            _selectedColors.value = colors.filter { it.id in colorIds }
        }
    }

    fun onErrorConsumed() {
        _errorMessage.value = null
    }

    fun save() {
        if (_isSaving.value) return
        if (_name.value.isBlank()) {
            _isNameError.value = true
            return
        }

        _isSaving.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
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
                    clothingRepository.updateItemWithColors(item, _selectedColors.value)
                } else {
                    clothingRepository.insertItemWithColors(item, _selectedColors.value)
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
            } finally {
                _isSaving.value = false
            }
        }
    }
}

sealed class ClothingFormEvent {
    data object NavigateBack : ClothingFormEvent()
}
