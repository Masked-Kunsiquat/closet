package com.closet.features.wardrobe

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.palette.graphics.Palette
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.AppError
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
    val brandQuery: String = "",
    val selectedBrandId: Long? = null,
    val allBrands: List<BrandEntity> = emptyList(),
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

private data class FormState(
    val name: String = "",
    val brandQuery: String = "",
    val selectedBrandId: Long? = null,
    val category: CategoryEntity? = null,
    val subcategory: SubcategoryEntity? = null,
    val price: String = "",
    val purchaseDate: LocalDate? = null,
    val purchaseLocation: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    val selectedColors: List<ColorEntity> = emptyList(),
    val isNameError: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: Int? = null
)

/**
 * ViewModel for managing the state of the Clothing form (Add or Edit).
 * Implements Hydration logic for editing existing items.
 */
@HiltViewModel
class ClothingFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lookupRepository: LookupRepository,
    private val brandRepository: BrandRepository,
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

    private val _form = MutableStateFlow(FormState(isLoading = isEditMode))

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

    val allBrands: StateFlow<List<BrandEntity>> = brandRepository.getAllBrands()
        .map { result -> if (result is DataResult.Success) result.data else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subcategories: StateFlow<List<SubcategoryEntity>> = _form
        .map { it.category }
        .distinctUntilChanged()
        .flatMapLatest { category ->
            if (category == null) flowOf(emptyList())
            else lookupRepository.getSubcategories(category.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<ClothingFormUiState> = combine(
        _form, categories, subcategories, allColors, allBrands
    ) { form, cats, subcats, colors, brands ->
        ClothingFormUiState(
            isEditMode = isEditMode,
            name = form.name,
            brandQuery = form.brandQuery,
            selectedBrandId = form.selectedBrandId,
            allBrands = brands,
            category = form.category,
            subcategory = form.subcategory,
            price = form.price,
            purchaseDate = form.purchaseDate,
            purchaseLocation = form.purchaseLocation,
            notes = form.notes,
            imagePath = form.imagePath,
            imageFile = form.imagePath?.let { storageRepository.getFile(it) },
            selectedColors = form.selectedColors,
            isNameError = form.isNameError,
            isSaving = form.isSaving,
            isLoading = form.isLoading,
            errorMessage = form.errorMessage,
            categories = cats,
            subcategories = subcats,
            allColors = colors,
            canSave = form.name.isNotBlank() && !form.isSaving &&
                    !(form.brandQuery.isNotBlank() && form.selectedBrandId == null),
            isDirty = computeIsDirty(form)
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

    private fun computeIsDirty(form: FormState): Boolean {
        val original = originalEntity
        if (isEditMode && original != null) {
            val colorsChanged = form.selectedColors.size != originalColors.size ||
                    !form.selectedColors.containsAll(originalColors)
            return form.name != original.name ||
                    form.selectedBrandId != original.brandId ||
                    form.category?.id != original.categoryId ||
                    form.subcategory?.id != original.subcategoryId ||
                    form.price != (original.purchasePrice?.toString() ?: "") ||
                    form.purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) != original.purchaseDate ||
                    form.purchaseLocation != (original.purchaseLocation ?: "") ||
                    form.notes != (original.notes ?: "") ||
                    form.imagePath != original.imagePath ||
                    colorsChanged
        }
        return form.name.isNotBlank() || form.brandQuery.isNotBlank() || form.selectedBrandId != null ||
                form.category != null || form.price.isNotBlank() || form.purchaseDate != null ||
                form.purchaseLocation.isNotBlank() || form.notes.isNotBlank() ||
                form.imagePath != null || form.selectedColors.isNotEmpty()
    }

    private fun loadItemForEditing(id: Long) {
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true) }
            try {
                val entityResult = clothingRepository.getItemEntityById(id)

                if (entityResult is DataResult.Success) {
                    val entity = entityResult.data
                    originalEntity = entity
                    originalImagePath = entity.imagePath

                    // Hydrate the fields
                    val brandName = if (entity.brandId != null) {
                        (brandRepository.getAllBrands().first { it is DataResult.Success } as DataResult.Success).data
                            .find { it.id == entity.brandId }?.name ?: ""
                    } else ""

                    val selectedCat = if (entity.categoryId != null) {
                        lookupRepository.getCategories().first().find { it.id == entity.categoryId }
                    } else null

                    val selectedSub = if (selectedCat != null && entity.subcategoryId != null) {
                        lookupRepository.getSubcategories(selectedCat.id).first()
                            .find { it.id == entity.subcategoryId }
                    } else null

                    val colors = clothingRepository.getItemColors(id).first()
                    originalColors = colors

                    _form.update { it.copy(
                        name = entity.name,
                        selectedBrandId = entity.brandId,
                        brandQuery = brandName,
                        price = entity.purchasePrice?.toString() ?: "",
                        purchaseDate = entity.purchaseDate?.let { d ->
                            try { LocalDate.parse(d) } catch (_: Exception) { null }
                        },
                        purchaseLocation = entity.purchaseLocation ?: "",
                        notes = entity.notes ?: "",
                        imagePath = entity.imagePath,
                        category = selectedCat,
                        subcategory = selectedSub,
                        selectedColors = colors,
                        isLoading = false
                    ) }
                } else {
                    _form.update { it.copy(
                        errorMessage = R.string.wardrobe_error_load_failed,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _form.update { it.copy(
                    errorMessage = R.string.wardrobe_error_load_failed,
                    isLoading = false
                ) }
            }
        }
    }

    fun updateName(newName: String) {
        _form.update { it.copy(name = newName, isNameError = false) }
    }

    fun onBrandQueryChange(text: String) {
        _form.update { it.copy(brandQuery = text, selectedBrandId = null) }
    }

    fun onBrandSelect(brand: BrandEntity) {
        _form.update { it.copy(selectedBrandId = brand.id, brandQuery = brand.name) }
    }

    fun onAddNewBrand(name: String) {
        val nameNormalized = name.trim()
        if (nameNormalized.isBlank()) return
        viewModelScope.launch {
            when (val result = brandRepository.insertBrand(nameNormalized)) {
                is DataResult.Success -> onBrandSelect(BrandEntity(id = result.data, name = nameNormalized))
                is DataResult.Error -> _form.update { it.copy(
                    errorMessage = when (result.error) {
                        is AppError.DatabaseError.ConstraintViolation -> R.string.wardrobe_error_brand_duplicate
                        else -> R.string.wardrobe_error_brand_save_failed
                    }
                ) }
                else -> Unit
            }
        }
    }

    fun selectCategory(category: CategoryEntity?) {
        _form.update { it.copy(category = category, subcategory = null) }
    }

    fun selectSubcategory(subcategory: SubcategoryEntity?) {
        _form.update { it.copy(subcategory = subcategory) }
    }

    fun updatePrice(newPrice: String) {
        if (newPrice.isEmpty() || newPrice.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
            _form.update { it.copy(price = newPrice) }
        }
    }

    fun updatePurchaseDate(date: LocalDate?) {
        _form.update { it.copy(purchaseDate = date) }
    }

    fun updatePurchaseLocation(location: String) {
        _form.update { it.copy(purchaseLocation = location) }
    }

    fun updateNotes(newNotes: String) {
        _form.update { it.copy(notes = newNotes) }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val previousPath = _form.value.imagePath
                val relativePath = storageRepository.saveImage(uri)
                _form.update { it.copy(imagePath = relativePath) }
                if (previousPath != null && previousPath != originalImagePath) {
                    storageRepository.deleteImage(previousPath)
                }
                extractColors(uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _form.update { it.copy(errorMessage = R.string.wardrobe_error_image_failed) }
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

                    _form.update { it.copy(selectedColors = snappedColors) }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Silently fail color extraction
        }
    }

    fun toggleColor(color: ColorEntity) {
        _form.update { current ->
            val updated = if (current.selectedColors.any { it.id == color.id }) {
                current.selectedColors.filter { it.id != color.id }
            } else {
                current.selectedColors + color
            }
            current.copy(selectedColors = updated)
        }
    }

    fun updateColors(colorIds: List<Long>) {
        viewModelScope.launch {
            val colors = lookupRepository.getColors().first()
            _form.update { it.copy(selectedColors = colors.filter { c -> c.id in colorIds }) }
        }
    }

    fun onErrorConsumed() {
        _form.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val currentForm = _form.value
        if (currentForm.isSaving) return
        if (currentForm.name.isBlank()) {
            _form.update { it.copy(isNameError = true) }
            return
        }
        if (currentForm.brandQuery.isNotBlank() && currentForm.selectedBrandId == null) return

        _form.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val form = _form.value
                val item = ClothingItemEntity(
                    id = itemId ?: 0,
                    name = form.name.trim(),
                    brandId = form.selectedBrandId,
                    categoryId = form.category?.id,
                    subcategoryId = form.subcategory?.id,
                    purchasePrice = form.price.toDoubleOrNull(),
                    purchaseDate = form.purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    purchaseLocation = form.purchaseLocation.trim().takeIf { it.isNotBlank() },
                    notes = form.notes.trim().takeIf { it.isNotBlank() },
                    imagePath = form.imagePath,
                    status = originalEntity?.status ?: ClothingStatus.Active,
                    washStatus = originalEntity?.washStatus ?: WashStatus.Clean,
                    isFavorite = originalEntity?.isFavorite ?: 0,
                    createdAt = originalEntity?.createdAt ?: Instant.now(),
                    updatedAt = Instant.now()
                )

                val result = if (isEditMode) {
                    clothingRepository.updateItemWithColors(item, form.selectedColors)
                } else {
                    clothingRepository.insertItemWithColors(item, form.selectedColors)
                }

                when (result) {
                    is DataResult.Success -> {
                        if (isEditMode && form.imagePath != originalImagePath) {
                            originalImagePath?.let { storageRepository.deleteImage(it) }
                        }
                        _events.send(ClothingFormEvent.NavigateBack)
                    }
                    is DataResult.Error -> {
                        _form.update { it.copy(errorMessage = R.string.wardrobe_error_save_failed) }
                    }
                    else -> { /* No-op */ }
                }
            } finally {
                _form.update { it.copy(isSaving = false) }
            }
        }
    }
}

sealed class ClothingFormEvent {
    data object NavigateBack : ClothingFormEvent()
}
