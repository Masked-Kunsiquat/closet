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
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.data.model.SizeValueEntity
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
 *
 * @property isEditMode True when editing an existing item; false for a new item.
 * @property canSave True when the form is valid and no save is in progress.
 * @property isDirty True when the form has unsaved changes relative to the original item (edit)
 *   or has any filled field (add).
 * @property errorMessage String resource ID for the current error, or null if none.
 * @property sizeSystems All available sizing systems (e.g. Letter, Numeric).
 * @property sizeValues Available size values filtered by the currently selected system.
 * @property selectedSizeSystemId ID of the currently selected size system.
 * @property selectedSizeValueId ID of the currently selected size value.
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
    val isDirty: Boolean = false,
    val sizeSystems: List<SizeSystemEntity> = emptyList(),
    val sizeValues: List<SizeValueEntity> = emptyList(),
    val selectedSizeSystemId: Long? = null,
    val selectedSizeValueId: Long? = null
)

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
    val errorMessage: Int? = null,
    val selectedSizeSystemId: Long? = null,
    val selectedSizeValueId: Long? = null
)

/**
 * ViewModel for managing the state of the Clothing form (Add or Edit).
 * Implements Hydration logic for editing existing items and auto-suggestion for sizing systems.
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

    /** Tracks if the user has manually changed the size system to prevent auto-suggestions from overriding. */
    private var sizeSystemUserOverridden = false

    // One-time events for navigation
    private val _events = Channel<ClothingFormEvent>()
    /** One-time navigation events (e.g. [ClothingFormEvent.NavigateBack]) for the screen to collect. */
    val events = _events.receiveAsFlow()

    /** All top-level categories, used to populate the category picker. */
    val categories: StateFlow<List<CategoryEntity>> = lookupRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All available colors, used to populate the color chip selector. */
    val allColors: StateFlow<List<ColorEntity>> = lookupRepository.getColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All brands for the autocomplete dropdown. */
    val allBrands: StateFlow<List<BrandEntity>> = brandRepository.getAllBrands()
        .map { result -> if (result is DataResult.Success) result.data else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Subcategories filtered by the currently selected category; resets to empty when no category is chosen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val subcategories: StateFlow<List<SubcategoryEntity>> = _form
        .map { it.category }
        .distinctUntilChanged()
        .flatMapLatest { category ->
            if (category == null) flowOf(emptyList())
            else lookupRepository.getSubcategories(category.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All size systems available for selection. */
    val sizeSystems: StateFlow<List<SizeSystemEntity>> = lookupRepository.getSizeSystems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Size values filtered by the currently selected size system. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sizeValues: StateFlow<List<SizeValueEntity>> = _form
        .map { it.selectedSizeSystemId }
        .distinctUntilChanged()
        .flatMapLatest { systemId ->
            if (systemId == null) flowOf(emptyList())
            else lookupRepository.getSizeValues(systemId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Consolidated UI state combining the form fields with all lookup lists. */
    val uiState: StateFlow<ClothingFormUiState> = combine(
        _form, categories, subcategories, allColors, allBrands, sizeSystems, sizeValues
    ) { form, cats, subs, colors, brands, systems, values ->
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
            subcategories = subs,
            allColors = colors,
            canSave = form.name.isNotBlank() && !form.isSaving &&
                    !(form.brandQuery.isNotBlank() && form.selectedBrandId == null),
            isDirty = computeIsDirty(form),
            sizeSystems = systems,
            sizeValues = values,
            selectedSizeSystemId = form.selectedSizeSystemId,
            selectedSizeValueId = form.selectedSizeValueId
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
                    colorsChanged ||
                    form.selectedSizeValueId != original.sizeValueId
        }
        return form.name.isNotBlank() || form.brandQuery.isNotBlank() || form.selectedBrandId != null ||
                form.category != null || form.price.isNotBlank() || form.purchaseDate != null ||
                form.purchaseLocation.isNotBlank() || form.notes.isNotBlank() ||
                form.imagePath != null || form.selectedColors.isNotEmpty() ||
                form.selectedSizeValueId != null
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

                    var systemId: Long? = null
                    if (entity.sizeValueId != null) {
                        // We need to find the system ID for the saved size value
                        val allSystems = lookupRepository.getSizeSystems().first()
                        for (system in allSystems) {
                            val values = lookupRepository.getSizeValues(system.id).first()
                            if (values.any { it.id == entity.sizeValueId }) {
                                systemId = system.id
                                break
                            }
                        }
                    }

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
                        selectedSizeSystemId = systemId,
                        selectedSizeValueId = entity.sizeValueId,
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

    /** Updates the item name field and clears any name validation error. */
    fun updateName(newName: String) {
        _form.update { it.copy(name = newName, isNameError = false) }
    }

    /** Updates the brand search text and clears any previously confirmed brand selection. */
    fun onBrandQueryChange(text: String) {
        _form.update { it.copy(brandQuery = text, selectedBrandId = null) }
    }

    /** Confirms a brand selection from the autocomplete dropdown. */
    fun onBrandSelect(brand: BrandEntity) {
        _form.update { it.copy(selectedBrandId = brand.id, brandQuery = brand.name) }
    }

    /**
     * Inserts a new brand with [name] and immediately selects it.
     * Surfaces an error in [uiState] if the name is a duplicate or the insert fails.
     */
    fun onAddNewBrand(name: String) {
        val nameNormalized = name.trim()
        if (nameNormalized.isBlank()) return
        viewModelScope.launch {
            when (val result = brandRepository.insertBrand(nameNormalized)) {
                is DataResult.Success -> onBrandSelect(BrandEntity(id = result.data, name = nameNormalized, normalizedName = nameNormalized.lowercase()))
                is DataResult.Error -> _form.update { it.copy(
                    errorMessage = when (result.throwable) {
                        is AppError.DatabaseError.ConstraintViolation -> R.string.wardrobe_error_brand_duplicate
                        else -> R.string.wardrobe_error_brand_save_failed
                    }
                ) }
                else -> Unit
            }
        }
    }

    /** Selects [category] and clears the subcategory since it belongs to the old category. Resets size override. */
    fun selectCategory(category: CategoryEntity?) {
        _form.update { it.copy(category = category, subcategory = null) }
        sizeSystemUserOverridden = false
    }

    /** Selects [subcategory] within the currently chosen category and triggers size system auto-suggestion. */
    fun selectSubcategory(subcategory: SubcategoryEntity?) {
        _form.update { it.copy(subcategory = subcategory) }
        
        if (!sizeSystemUserOverridden) {
            val defaultSystemName = defaultSizeSystemName(subcategory?.name)
            if (defaultSystemName != null) {
                viewModelScope.launch {
                    val systems = sizeSystems.value
                    systems.find { it.name == defaultSystemName }?.let { 
                        selectSizeSystem(it, isUserOverride = false) 
                    }
                }
            }
        }
    }

    /** Mapping from subcategory name to its canonical default sizing system. */
    private fun defaultSizeSystemName(subcategoryName: String?): String? = when (subcategoryName) {
        "Sneakers", "Boots", "Sandals", "Dress Shoes", "Slippers" -> "Shoes (US Men's)"
        "Belt", "Hat/Cap", "Scarf", "Sunglasses", "Watch",
        "Jewelry", "Tie", "Cufflinks",
        "Backpack", "Tote", "Crossbody", "Duffel"              -> "One Size"
        null                                                     -> null
        else                                                     -> "Letter"
    }

    /** Updates the price field, accepting only valid decimal strings (up to 2 decimal places). */
    fun updatePrice(newPrice: String) {
        if (newPrice.isEmpty() || newPrice.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
            _form.update { it.copy(price = newPrice) }
        }
    }

    /** Updates the purchase date field. */
    fun updatePurchaseDate(date: LocalDate?) {
        _form.update { it.copy(purchaseDate = date) }
    }

    /** Updates the purchase location text field. */
    fun updatePurchaseLocation(location: String) {
        _form.update { it.copy(purchaseLocation = location) }
    }

    /** Updates the notes text field. */
    fun updateNotes(newNotes: String) {
        _form.update { it.copy(notes = newNotes) }
    }

    /**
     * Copies the image at [uri] into app-owned storage, stores its relative path, and
     * auto-suggests colors from the image palette. Deletes any previously staged (unsaved)
     * replacement image. No-ops if [uri] is null.
     */
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

    /** Adds [color] to the selection if not present, or removes it if already selected. */
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

    /** Clears the transient error message once the UI has consumed it. */
    fun onErrorConsumed() {
        _form.update { it.copy(errorMessage = null) }
    }

    /**
     * Selects a size system and clears the current size value.
     * @param system The sizing system to select.
     * @param isUserOverride Set to true if this was an explicit user interaction, blocking future auto-suggestions.
     */
    fun selectSizeSystem(system: SizeSystemEntity?, isUserOverride: Boolean = true) {
        if (isUserOverride) sizeSystemUserOverridden = true
        _form.update { it.copy(selectedSizeSystemId = system?.id, selectedSizeValueId = null) }
    }

    /** Selects a specific size value. */
    fun selectSizeValue(value: SizeValueEntity?) {
        _form.update { it.copy(selectedSizeValueId = value?.id) }
    }

    /** Clears both the size system and size value, and resets the user override flag. */
    fun clearSize() {
        sizeSystemUserOverridden = false
        _form.update { it.copy(selectedSizeSystemId = null, selectedSizeValueId = null) }
    }

    /**
     * Validates the form and persists the item (insert on add, update on edit).
     * Emits [ClothingFormEvent.NavigateBack] on success.
     * Sets [ClothingFormUiState.isNameError] if the name is blank, or surfaces a save error
     * via [ClothingFormUiState.errorMessage] if the repository call fails.
     */
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
                    sizeValueId = form.selectedSizeValueId,
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

/** One-time navigation events emitted by [ClothingFormViewModel]. */
sealed class ClothingFormEvent {
    /** Emitted when the item was saved successfully; the screen should navigate back. */
    data object NavigateBack : ClothingFormEvent()
}
