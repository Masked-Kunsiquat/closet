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

    private var originalImagePath: String? = null
    private var originalEntity: ClothingItemEntity? = null
    private var originalColors: List<ColorEntity> = emptyList()

    private var sizeSystemUserOverridden = false

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

    val sizeSystems: StateFlow<List<SizeSystemEntity>> = lookupRepository.getSizeSystems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sizeValues: StateFlow<List<SizeValueEntity>> = _form
        .map { it.selectedSizeSystemId }
        .distinctUntilChanged()
        .flatMapLatest { systemId ->
            if (systemId == null) flowOf(emptyList())
            else lookupRepository.getSizeValues(systemId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<ClothingFormUiState> = combine(
        _form,
        categories,
        subcategories,
        allColors,
        allBrands,
        sizeSystems,
        sizeValues
    ) { args: Array<Any?> ->
        val form = args[0] as FormState
        val cats = args[1] as List<CategoryEntity>
        val subs = args[2] as List<SubcategoryEntity>
        val colors = args[3] as List<ColorEntity>
        val brands = args[4] as List<BrandEntity>
        val systems = args[5] as List<SizeSystemEntity>
        val values = args[6] as List<SizeValueEntity>

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
        val original = originalEntity ?: return form.name.isNotBlank() ||
                form.brandQuery.isNotBlank() || form.selectedBrandId != null ||
                form.category != null || form.price.isNotBlank() || form.purchaseDate != null ||
                form.purchaseLocation.isNotBlank() || form.notes.isNotBlank() ||
                form.imagePath != null || form.selectedColors.isNotEmpty() ||
                form.selectedSizeValueId != null

        val colorsChanged = form.selectedColors.size != originalColors.size ||
                !form.selectedColors.containsAll(originalColors)
        
        val formDateString = form.purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        return form.name != original.name ||
                form.selectedBrandId != original.brandId ||
                form.category?.id != original.categoryId ||
                form.subcategory?.id != original.subcategoryId ||
                form.price != (original.purchasePrice?.toString() ?: "") ||
                formDateString != original.purchaseDate ||
                form.purchaseLocation != (original.purchaseLocation ?: "") ||
                form.notes != (original.notes ?: "") ||
                form.imagePath != original.imagePath ||
                colorsChanged ||
                form.selectedSizeValueId != original.sizeValueId
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
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _form.update { it.copy(isLoading = false, errorMessage = com.closet.core.ui.R.string.error_database_query) }
            }
        }
    }

    fun onNameChange(name: String) {
        _form.update { it.copy(name = name, isNameError = false) }
    }

    fun onBrandQueryChange(query: String) {
        _form.update { it.copy(brandQuery = query, selectedBrandId = null) }
    }

    fun onBrandSelected(brand: BrandEntity?) {
        _form.update { it.copy(selectedBrandId = brand?.id, brandQuery = brand?.name ?: "") }
    }

    fun onCategorySelected(category: CategoryEntity?) {
        sizeSystemUserOverridden = false
        _form.update { it.copy(category = category, subcategory = null) }
    }

    fun onSubcategorySelected(subcategory: SubcategoryEntity?) {
        if (!sizeSystemUserOverridden) {
            val targetName = defaultSizeSystemName(subcategory?.name)
            val systemId = sizeSystems.value.find { it.name == targetName }?.id
            _form.update { it.copy(subcategory = subcategory, selectedSizeSystemId = systemId, selectedSizeValueId = null) }
        } else {
            _form.update { it.copy(subcategory = subcategory) }
        }
    }

    private fun defaultSizeSystemName(subcategoryName: String?): String? = when (subcategoryName) {
        "Sneakers", "Boots", "Sandals", "Dress Shoes", "Slippers" -> "Shoes (US Men's)"
        "Belt", "Hat/Cap", "Scarf", "Sunglasses", "Watch",
        "Jewelry", "Tie", "Cufflinks",
        "Backpack", "Tote", "Crossbody", "Duffel"               -> "One Size"
        null                                                      -> null
        else                                                      -> "Letter"
    }

    fun onPriceChange(price: String) {
        _form.update { it.copy(price = price) }
    }

    fun onDateChange(date: LocalDate?) {
        _form.update { it.copy(purchaseDate = date) }
    }

    fun onLocationChange(location: String) {
        _form.update { it.copy(purchaseLocation = location) }
    }

    fun onNotesChange(notes: String) {
        _form.update { it.copy(notes = notes) }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true) }
            try {
                val path = storageRepository.saveImage(uri)
                _form.update { it.copy(imagePath = path, isLoading = false) }
                
                val file = storageRepository.getFile(path)
                extractColorsFromFile(file)
            } catch (e: Exception) {
                _form.update { it.copy(isLoading = false, errorMessage = com.closet.core.ui.R.string.error_unexpected) }
            }
        }
    }

    private suspend fun extractColorsFromFile(file: File) {
        withContext(Dispatchers.Default) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val extracted = listOfNotNull(
                        palette.dominantSwatch?.rgb,
                        palette.vibrantSwatch?.rgb,
                        palette.mutedSwatch?.rgb
                    ).distinct()

                    if (extracted.isNotEmpty()) {
                        val availableColors = allColors.value
                        val matched = extracted.map { ColorMatcher.findNearestColor(it, availableColors) }.distinct()
                        if (matched.isNotEmpty()) {
                            _form.update { it.copy(selectedColors = matched) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun onColorToggle(color: ColorEntity) {
        _form.update { state ->
            val newColors = if (state.selectedColors.contains(color)) {
                state.selectedColors - color
            } else {
                state.selectedColors + color
            }
            state.copy(selectedColors = newColors)
        }
    }

    fun onSizeSystemSelected(systemId: Long?) {
        sizeSystemUserOverridden = true
        _form.update { it.copy(selectedSizeSystemId = systemId, selectedSizeValueId = null) }
    }

    fun onSizeValueSelected(valueId: Long?) {
        _form.update { it.copy(selectedSizeValueId = valueId) }
    }

    fun save() {
        val state = _form.value
        if (state.name.isBlank()) {
            _form.update { it.copy(isNameError = true) }
            return
        }

        viewModelScope.launch {
            _form.update { it.copy(isSaving = true) }
            try {
                val item = ClothingItemEntity(
                    id = itemId ?: 0L,
                    name = state.name,
                    brandId = state.selectedBrandId,
                    categoryId = state.category?.id,
                    subcategoryId = state.subcategory?.id,
                    purchasePrice = state.price.toDoubleOrNull(),
                    purchaseDate = state.purchaseDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    purchaseLocation = state.purchaseLocation.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    imagePath = state.imagePath,
                    sizeValueId = state.selectedSizeValueId,
                    status = originalEntity?.status ?: ClothingStatus.Active,
                    washStatus = originalEntity?.washStatus ?: WashStatus.Clean,
                    createdAt = originalEntity?.createdAt ?: Instant.now(),
                    updatedAt = Instant.now()
                )

                val result = if (isEditMode) {
                    clothingRepository.updateItemWithColors(item, state.selectedColors)
                } else {
                    clothingRepository.insertItemWithColors(item, state.selectedColors)
                }

                if (result is DataResult.Success) {
                    if (isEditMode && originalImagePath != null && originalImagePath != state.imagePath) {
                        storageRepository.deleteImage(originalImagePath!!)
                    }
                    _events.send(ClothingFormEvent.NavigateBack)
                } else {
                    _form.update { it.copy(isSaving = false, errorMessage = com.closet.core.ui.R.string.error_database_query) }
                }
            } catch (e: Exception) {
                _form.update { it.copy(isSaving = false, errorMessage = com.closet.core.ui.R.string.error_database_query) }
            }
        }
    }

    fun cancel() {
        val currentPath = _form.value.imagePath
        if (currentPath != null && currentPath != originalImagePath) {
            viewModelScope.launch {
                storageRepository.deleteImage(currentPath)
            }
        }
        viewModelScope.launch {
            _events.send(ClothingFormEvent.NavigateBack)
        }
    }
}

sealed class ClothingFormEvent {
    object NavigateBack : ClothingFormEvent()
}
