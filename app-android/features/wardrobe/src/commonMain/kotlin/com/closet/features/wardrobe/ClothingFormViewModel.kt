package com.closet.features.wardrobe

import co.touchlab.kermit.Logger
import com.closet.core.ui.resources.error_database_query
import com.closet.core.ui.resources.error_unexpected
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.StringResource
import com.closet.features.wardrobe.resources.wardrobe_model_download_error
import com.closet.features.wardrobe.resources.wardrobe_segmentation_error
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.MaterialEntity
import com.closet.core.data.model.OccasionEntity
import com.closet.core.data.model.PatternEntity
import com.closet.core.data.model.SeasonEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.data.model.SizeValueEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.model.TaggedColor
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.time.AppClock
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.util.DataResult
import com.closet.features.wardrobe.photo.ItemPhotoProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

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
    /** Absolute path of [imagePath], for image loaders. */
    val resolvedImagePath: String? = null,
    val isSegmenting: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val hasSegmentedImage: Boolean = false,
    /** Absolute path of the pre-segmentation image while "Undo" is available. */
    val resolvedOriginalImagePath: String? = null,
    val isSegmentationSupported: Boolean = true,
    val isCaptioning: Boolean = false,         // spinner hint for future UI polish
    val imageCaption: String? = null,          // surfaced for debug/review if needed
    val selectedColors: List<ColorEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val allColors: List<ColorEntity> = emptyList(),
    val isNameError: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: StringResource? = null,
    val canSave: Boolean = false,
    val isDirty: Boolean = false,
    val sizeSystems: List<SizeSystemEntity> = emptyList(),
    val sizeValues: List<SizeValueEntity> = emptyList(),
    val selectedSizeSystemId: Long? = null,
    val selectedSizeValueId: Long? = null,
    val status: ClothingStatus = ClothingStatus.Active,
    val selectedSeasonIds: Set<Long> = emptySet(),
    val selectedOccasionIds: Set<Long> = emptySet(),
    val selectedMaterialIds: Set<Long> = emptySet(),
    val selectedPatternIds: Set<Long> = emptySet(),
    val allSeasons: List<SeasonEntity> = emptyList(),
    val allOccasions: List<OccasionEntity> = emptyList(),
    val allMaterials: List<MaterialEntity> = emptyList(),
    val allPatterns: List<PatternEntity> = emptyList(),
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
    val isSegmenting: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val hasSegmentedImage: Boolean = false,
    val originalSegmentationImagePath: String? = null,
    val originalSegmentationImageCaption: String? = null,
    val isCaptioning: Boolean = false,
    val imageCaption: String? = null,
    val selectedColors: List<ColorEntity> = emptyList(),
    /**
     * The measured row for each colour that has one: from the latest extraction (source auto,
     * with hex and share) or, on edit, the item's stored rows as they are. Picking a new photo
     * clears every hex and share (they measured the old photo) but keeps the rows' sources.
     * Saving uses it for any selected colour it covers; other selected colours become plain user rows.
     */
    val measured: Map<Long, TaggedColor> = emptyMap(),
    /** True after any manual colour toggle; a new extraction that replaces the selection resets it. */
    val colorsEditedByUser: Boolean = false,
    /**
     * True while the selection is an extraction's output from this session (set when an extraction
     * replaces it, cleared by any toggle). Saving then stores `colors_curated = false`.
     */
    val colorsFromExtraction: Boolean = false,
    /** The colour selection when background removal started; undo or a failed removal restores it. */
    val preSegmentationColors: ColorSelection? = null,
    val isNameError: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    /** True when the edit load failed; the form never saves, since it would overwrite the item with partial data. */
    val loadFailed: Boolean = false,
    val errorMessage: StringResource? = null,
    val selectedSizeSystemId: Long? = null,
    val selectedSizeValueId: Long? = null,
    val status: ClothingStatus = ClothingStatus.Active,
    val selectedSeasonIds: Set<Long> = emptySet(),
    val selectedOccasionIds: Set<Long> = emptySet(),
    val selectedMaterialIds: Set<Long> = emptySet(),
    val selectedPatternIds: Set<Long> = emptySet(),
) {
    /**
     * True when the selection holds a user's choice: a manual toggle, a stored user row, or a selected
     * colour with no measurement (a user pick). Re-extracting the same photo must not replace it.
     * Not the stored `colors_curated` flag; [ClothingFormViewModel.save] works that out.
     */
    val selectionCurated: Boolean
        get() = colorsEditedByUser ||
            measured.values.any { it.source == ColorSource.USER } ||
            selectedColors.any { it.id !in measured }
}

/** A snapshot of the form's colour selection; [toggleGeneration] is the toggle count when it was taken. */
private data class ColorSelection(
    val selectedColors: List<ColorEntity>,
    val measured: Map<Long, TaggedColor>,
    val colorsEditedByUser: Boolean,
    val colorsFromExtraction: Boolean,
    val toggleGeneration: Long,
)

/**
 * ViewModel for managing the state of the Clothing form (Add or Edit).
 */
class ClothingFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val lookupRepository: LookupRepository,
    private val brandRepository: BrandRepository,
    private val clothingRepository: ClothingRepository,
    private val photoProcessor: ItemPhotoProcessor,
    private val imagePathResolver: ImagePathResolver,
    private val shortcutUsageReporter: ShortcutUsageReporter,
    private val clock: AppClock,
) : ViewModel() {

    private val log = Logger.withTag("ClothingFormViewModel")

    private val itemId: Long? = try { savedStateHandle.toRoute<EditClothingDestination>().itemId } catch (_: Exception) { null }
    private val isEditMode = itemId != null

    private val _form = MutableStateFlow(FormState(isLoading = isEditMode))

    private var originalImagePath: String? = null
    private var originalEntity: ClothingItemEntity? = null
    private var originalColors: List<ColorEntity> = emptyList()
    private var originalSeasonIds: Set<Long> = emptySet()
    private var originalOccasionIds: Set<Long> = emptySet()
    private var originalMaterialIds: Set<Long> = emptySet()
    private var originalPatternIds: Set<Long> = emptySet()

    private var sizeSystemUserOverridden = false
    private var captionJob: Job? = null

    /**
     * Bumped by every manual colour toggle. The caller of an extraction captures it as soon as it
     * publishes the image, and the extraction drops its result if it changed meanwhile, so a toggle
     * made after the image appears (during cleanup or measuring) is never overwritten.
     */
    private var colorToggleGeneration = 0L

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

    val allSeasons: StateFlow<List<SeasonEntity>> = lookupRepository.getSeasons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOccasions: StateFlow<List<OccasionEntity>> = lookupRepository.getOccasions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMaterials: StateFlow<List<MaterialEntity>> = lookupRepository.getMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatterns: StateFlow<List<PatternEntity>> = lookupRepository.getPatterns()
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
        sizeValues,
        allSeasons,
        allOccasions,
        allMaterials,
        allPatterns,
    ) { args: Array<Any?> ->
        val form = args[0] as FormState
        val cats = args[1] as List<CategoryEntity>
        val subs = args[2] as List<SubcategoryEntity>
        val colors = args[3] as List<ColorEntity>
        val brands = args[4] as List<BrandEntity>
        val systems = args[5] as List<SizeSystemEntity>
        val values = args[6] as List<SizeValueEntity>
        val seasons = args[7] as List<SeasonEntity>
        val occasions = args[8] as List<OccasionEntity>
        val materials = args[9] as List<MaterialEntity>
        val patterns = args[10] as List<PatternEntity>

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
            resolvedImagePath = form.imagePath?.let { imagePathResolver.absolutePath(it) },
            isSegmenting = form.isSegmenting,
            isDownloadingModel = form.isDownloadingModel,
            hasSegmentedImage = form.hasSegmentedImage,
            resolvedOriginalImagePath = form.originalSegmentationImagePath?.let { imagePathResolver.absolutePath(it) },
            isSegmentationSupported = photoProcessor.isSegmentationSupported,
            isCaptioning = form.isCaptioning,
            imageCaption = form.imageCaption,
            selectedColors = form.selectedColors,
            isNameError = form.isNameError,
            isSaving = form.isSaving,
            isLoading = form.isLoading,
            errorMessage = form.errorMessage,
            categories = cats,
            subcategories = subs,
            allColors = colors,
            canSave = !form.loadFailed && form.name.isNotBlank() && !form.isSaving && !form.isSegmenting &&
                    !form.isDownloadingModel &&
                    !(form.brandQuery.isNotBlank() && form.selectedBrandId == null),
            isDirty = computeIsDirty(form),
            sizeSystems = systems,
            sizeValues = values,
            selectedSizeSystemId = form.selectedSizeSystemId,
            selectedSizeValueId = form.selectedSizeValueId,
            status = form.status,
            selectedSeasonIds = form.selectedSeasonIds,
            selectedOccasionIds = form.selectedOccasionIds,
            selectedMaterialIds = form.selectedMaterialIds,
            selectedPatternIds = form.selectedPatternIds,
            allSeasons = seasons,
            allOccasions = occasions,
            allMaterials = materials,
            allPatterns = patterns,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClothingFormUiState(isEditMode = isEditMode, isLoading = isEditMode)
    )

    private val openCamera: Boolean = try { savedStateHandle.toRoute<AddClothingDestination>().openCamera } catch (_: Exception) { false }

    init {
        if (isEditMode && itemId != null) {
            loadItemForEditing(itemId)
        }
        if (openCamera) {
            viewModelScope.launch { _events.send(ClothingFormEvent.OpenImagePicker) }
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
        
        val formDateString = form.purchaseDate?.toString() // ISO yyyy-MM-dd
        
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
                form.selectedSizeValueId != original.sizeValueId ||
                form.status != original.status ||
                form.selectedSeasonIds != originalSeasonIds ||
                form.selectedOccasionIds != originalOccasionIds ||
                form.selectedMaterialIds != originalMaterialIds ||
                form.selectedPatternIds != originalPatternIds
    }

    private fun loadItemForEditing(id: Long) {
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true) }
            try {
                val entityResult = clothingRepository.getItemEntityById(id)
                // A failed detail read must fail the load (#126): treating it as "no colours, seasons,
                // occasions, materials or patterns" would let a save wipe them.
                val detail = when (val result = clothingRepository.getItemDetailOnce(id)) {
                    is DataResult.Success -> result.data
                    is DataResult.Error -> throw result.throwable
                    DataResult.Loading -> null
                }

                if (entityResult is DataResult.Success) {
                    val entity = entityResult.data

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

                    val colors = detail?.colors ?: emptyList()
                    // Re-saving must keep each row's source, hex and share, so a failed read of the
                    // rows fails the load rather than letting a save flip them to plain user rows.
                    val colorRows = when (val rows = clothingRepository.getItemColorRows(id)) {
                        is DataResult.Success -> rows.data
                        is DataResult.Error -> throw rows.throwable
                        DataResult.Loading -> emptyList()
                    }
                    val measured = colorRows.associate {
                        it.colorId to TaggedColor(it.colorId, hex = it.hex, share = it.share, source = it.source)
                    }
                    val seasonIds = detail?.seasons?.map { it.id }?.toSet() ?: emptySet()
                    val occasionIds = detail?.occasions?.map { it.id }?.toSet() ?: emptySet()
                    val materialIds = detail?.materials?.map { it.id }?.toSet() ?: emptySet()
                    val patternIds = detail?.patterns?.map { it.id }?.toSet() ?: emptySet()

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

                    // Every read succeeded: only now does the form become an edit of this item.
                    originalEntity = entity
                    originalImagePath = entity.imagePath
                    originalColors = colors
                    originalSeasonIds = seasonIds
                    originalOccasionIds = occasionIds
                    originalMaterialIds = materialIds
                    originalPatternIds = patternIds

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
                        // PNG (API < 30) or WebP (API 30+) suffix means the image was previously
                        // segmented; hide the "Remove background" button and suppress "Undo".
                        hasSegmentedImage = entity.imagePath?.let {
                            it.endsWith(".png") || it.endsWith(".webp")
                        } == true,
                        category = selectedCat,
                        subcategory = selectedSub,
                        selectedColors = colors,
                        measured = measured,
                        colorsEditedByUser = false,
                        selectedSizeSystemId = systemId,
                        selectedSizeValueId = entity.sizeValueId,
                        imageCaption = entity.imageCaption,
                        status = entity.status,
                        selectedSeasonIds = originalSeasonIds,
                        selectedOccasionIds = originalOccasionIds,
                        selectedMaterialIds = originalMaterialIds,
                        selectedPatternIds = originalPatternIds,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.e(e) { "edit load failed for item $id" }
                _form.update { it.copy(isLoading = false, loadFailed = true, errorMessage = CoreRes.string.error_database_query) }
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

    /** [source] is the picked image's platform URI string (on Android, a content URI). */
    fun onImageSelected(source: String?) {
        if (source == null) return
        // Don't allow a new image to be swapped in while the segmentation coroutine
        // is in flight — it may be actively reading the current imagePath from disk.
        // The photo-picker button is disabled in the UI during these states, but guard
        // here too in case the call arrives via another code path.
        val form = _form.value
        if (form.isSegmenting || form.isDownloadingModel) return

        viewModelScope.launch {
            _form.update { it.copy(isLoading = true) }
            try {
                val previousPath = _form.value.imagePath
                val segOrigPath = _form.value.originalSegmentationImagePath
                val path = photoProcessor.importPhoto(source)
                // Point the UI at the new file before any cleanup so the form always
                // references a valid path even if a subsequent deletion fails.
                // Reset caption fields so a stale result from a prior image can't bleed through.
                // The measured hexes and shares belong to the previous image: drop them, keeping each
                // row's source and the selection, so a save before (or without) a successful
                // extraction of the new image never stores the old photo's measurements.
                captionJob?.cancel()
                _form.update { it.copy(
                    imagePath = path,
                    isLoading = false,
                    hasSegmentedImage = false,
                    originalSegmentationImagePath = null,
                    originalSegmentationImageCaption = null,
                    preSegmentationColors = null,
                    imageCaption = null,
                    measured = it.measured.mapValues { (_, row) -> row.copy(hex = null, share = null) },
                ) }
                // A toggle from here on is a choice about the new photo; the cleanup below suspends,
                // so capture now rather than inside extractColors.
                val generation = colorToggleGeneration
                // Best-effort cleanup — failures must not block the form update above.
                if (previousPath != null && previousPath != originalImagePath) {
                    runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(previousPath) } }
                        .onFailure { log.e(it) { "onImageSelected: failed to delete previous $previousPath" } }
                }
                // Clean up the pre-segmentation intermediate file if the user picks a new image
                if (segOrigPath != null && segOrigPath != originalImagePath && segOrigPath != previousPath) {
                    runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(segOrigPath) } }
                        .onFailure { log.e(it) { "onImageSelected: failed to delete seg original $segOrigPath" } }
                }
                extractColors(path, fromNewPhoto = true, generation = generation)

                // At-capture captioning — best-effort, never blocks save.
                if (photoProcessor.isCaptionSupported) {
                    launchCaptionJob(path)
                }
            } catch (e: Exception) {
                log.e(e) { "image selection failed" }
                _form.update { it.copy(isLoading = false, errorMessage = CoreRes.string.error_unexpected) }
            }
        }
    }

    fun removeBackground() {
        val currentForm = _form.value
        if (!photoProcessor.isSegmentationSupported || currentForm.imagePath == null ||
            currentForm.isSegmenting || currentForm.isDownloadingModel) return

        // The processor moves its blocking work (model delivery, decode, segmentation, save) to IO.
        viewModelScope.launch {
            // Always ensure the model rather than gating on a "downloaded" check: that status is
            // optimistic (no public Play Services status API), so a gate would permanently skip
            // this step and hide first-run delivery failures.
            _form.update { it.copy(isDownloadingModel = true) }
            try {
                photoProcessor.ensureSegmentationModel()
            } catch (e: CancellationException) {
                _form.update { it.copy(isDownloadingModel = false) }
                throw e
            } catch (e: Exception) {
                log.e(e) { "removeBackground: model download failed" }
                _form.update { it.copy(
                    isDownloadingModel = false,
                    errorMessage = Res.string.wardrobe_model_download_error,
                ) }
                return@launch
            }
            _form.update { it.copy(isDownloadingModel = false) }

            _form.update { it.copy(
                isSegmenting = true,
                originalSegmentationImagePath = it.imagePath,
                originalSegmentationImageCaption = it.imageCaption,
                preSegmentationColors = ColorSelection(
                    selectedColors = it.selectedColors,
                    measured = it.measured,
                    colorsEditedByUser = it.colorsEditedByUser,
                    colorsFromExtraction = it.colorsFromExtraction,
                    toggleGeneration = colorToggleGeneration,
                ),
            ) }
            try {
                val path = _form.value.imagePath ?: return@launch
                // Decodes at ≤1024 px, segments, and saves the result as a new PNG.
                val savedPath = photoProcessor.removeBackground(path)
                _form.update { it.copy(
                    imagePath = savedPath,
                    hasSegmentedImage = true,
                    isSegmenting = false,
                    imageCaption = null,                      // old caption was for the un-segmented image
                    originalSegmentationImageCaption = null,  // stash consumed; caption lifecycle restarts
                ) }
                val generation = colorToggleGeneration
                // Re-extract colours from the segmented PNG. Transparent pixels are ignored,
                // so only subject colours are sampled — not the background. Same photo, so a
                // curated selection is kept.
                extractColors(savedPath, fromNewPhoto = false, generation = generation)
                // Re-caption the segmented image on Main (Image Description API requirement).
                if (photoProcessor.isCaptionSupported) {
                    launchCaptionJob(savedPath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "removeBackground failed" }
                _form.update { it.withPreSegmentationColors().copy(
                    imagePath = it.originalSegmentationImagePath,
                    originalSegmentationImagePath = null,
                    imageCaption = it.originalSegmentationImageCaption,
                    originalSegmentationImageCaption = null,
                    isSegmenting = false,
                    errorMessage = Res.string.wardrobe_segmentation_error,
                ) }
            }
        }
    }

    fun revertSegmentation() {
        val currentPath = _form.value.imagePath
        val originalPath = _form.value.originalSegmentationImagePath ?: return
        // Cancel any caption job that was started for the segmented PNG.
        // The stale-path guard would block its writes, but isCaptioning would
        // stay stuck true without an explicit cancel + reset here.
        captionJob?.cancel()
        _form.update { it.withPreSegmentationColors().copy(
            imagePath = originalPath,
            hasSegmentedImage = false,
            originalSegmentationImagePath = null,
            imageCaption = it.originalSegmentationImageCaption,
            originalSegmentationImageCaption = null,
            isCaptioning = false,
        ) }
        if (currentPath != null && currentPath != originalPath) {
            viewModelScope.launch {
                try {
                    photoProcessor.deleteImage(currentPath)
                } catch (e: Exception) {
                    log.d(e) { "revertSegmentation: failed to delete segmented PNG $currentPath" }
                }
            }
        }
    }

    fun onErrorConsumed() {
        _form.update { it.copy(errorMessage = null) }
    }

    /**
     * Cancels any in-flight caption job and launches a new one for [path].
     *
     * Captures [path] as the "owning" path for this request. All state mutations are
     * guarded by a check that the form's current [FormState.imagePath] still equals
     * [path], so a stale job that was cancelled late cannot overwrite state that
     * belongs to a newer image selection.
     *
     * Must be called from a coroutine context that can reach Main (i.e. viewModelScope).
     */
    private fun launchCaptionJob(path: String) {
        // A late call for an image that is no longer current must not cancel the current
        // image's caption job.
        if (_form.value.imagePath != path) return
        captionJob?.cancel()
        captionJob = viewModelScope.launch(Dispatchers.Main) {
            if (_form.value.imagePath == path) _form.update { it.copy(isCaptioning = true) }
            try {
                // The processor decodes on IO and calls the captioner on Main (Image
                // Description API requirement); null means the image could not be decoded.
                val caption = photoProcessor.describe(path)
                if (caption == null) {
                    if (_form.value.imagePath == path) _form.update { it.copy(isCaptioning = false) }
                    return@launch
                }
                if (_form.value.imagePath == path) {
                    _form.update { it.copy(imageCaption = caption, isCaptioning = false) }
                }
            } catch (e: CancellationException) {
                if (_form.value.imagePath == path) _form.update { it.copy(isCaptioning = false) }
                throw e
            } catch (e: Exception) {
                log.d(e) { "at-capture caption failed — ignored" }
                if (_form.value.imagePath == path) _form.update { it.copy(isCaptioning = false) }
            }
        }
    }

    /**
     * Replaces the selected colors with those measured from [path] (as auto rows) and clears the
     * user-edited flag.
     *
     * A new photo ([fromNewPhoto] true) always replaces the selection. Re-measuring the same photo
     * (background removal, [fromNewPhoto] false) replaces it only while it is untouched extractor
     * output; a curated selection ([FormState.selectionCurated]) is kept, so segmenting never turns
     * user rows into auto ones. Applying a result sets [FormState.colorsFromExtraction].
     *
     * Leaves the selection as it is if none of the colours match, if the reference shades can't be
     * loaded, if a colour was toggled since [path] was published ([generation], the
     * [colorToggleGeneration] the caller captured right after publishing it), or if [path] is no
     * longer the current image by the time extraction finishes.
     */
    private suspend fun extractColors(path: String, fromNewPhoto: Boolean, generation: Long) {
        if (!fromNewPhoto && _form.value.selectionCurated) return
        val shades = referenceShades() ?: return
        val extracted = photoProcessor.extractColors(path, shades)
        if (colorToggleGeneration != generation) return
        val colorsById = allColors.value.associateBy { it.id }
        val known = extracted.filter { it.colorId in colorsById }
        if (known.isEmpty()) return
        val measured = known.associate {
            it.colorId to TaggedColor(it.colorId, hex = it.hex, share = it.share, source = ColorSource.AUTO)
        }
        _form.update {
            if (it.imagePath != path || (!fromNewPhoto && it.selectionCurated)) it
            else it.copy(
                selectedColors = known.map { c -> colorsById.getValue(c.colorId) },
                measured = measured,
                colorsEditedByUser = false,
                colorsFromExtraction = true,
            )
        }
    }

    /**
     * Restores the colour selection snapshotted when background removal started and drops the
     * snapshot. A colour toggled since then is the user's choice, so the current selection is kept.
     */
    private fun FormState.withPreSegmentationColors(): FormState {
        val snapshot = preSegmentationColors ?: return this
        val restored = if (snapshot.toggleGeneration == colorToggleGeneration) {
            copy(
                selectedColors = snapshot.selectedColors,
                measured = snapshot.measured,
                colorsEditedByUser = snapshot.colorsEditedByUser,
                colorsFromExtraction = snapshot.colorsFromExtraction,
            )
        } else {
            this
        }
        return restored.copy(preSegmentationColors = null)
    }

    private var cachedShades: List<ReferenceShade>? = null

    /** The colour namer's reference shades, loaded once; null (skip extraction) when the load fails. */
    private suspend fun referenceShades(): List<ReferenceShade>? {
        cachedShades?.let { return it }
        return when (val result = lookupRepository.getReferenceShades()) {
            is DataResult.Success -> result.data.also { cachedShades = it }
            is DataResult.Error -> {
                log.w(result.throwable) { "reference shades failed to load; colour extraction skipped" }
                null
            }
            DataResult.Loading -> null
        }
    }

    fun onColorToggle(color: ColorEntity) {
        colorToggleGeneration++
        _form.update { state ->
            val newColors = if (state.selectedColors.contains(color)) {
                state.selectedColors - color
            } else {
                state.selectedColors + color
            }
            state.copy(selectedColors = newColors, colorsEditedByUser = true, colorsFromExtraction = false)
        }
    }

    fun onSeasonToggle(id: Long) {
        _form.update { it.copy(selectedSeasonIds = it.selectedSeasonIds.toggle(id)) }
    }

    fun onOccasionToggle(id: Long) {
        _form.update { it.copy(selectedOccasionIds = it.selectedOccasionIds.toggle(id)) }
    }

    fun onMaterialToggle(id: Long) {
        _form.update { it.copy(selectedMaterialIds = it.selectedMaterialIds.toggle(id)) }
    }

    fun onPatternToggle(id: Long) {
        _form.update { it.copy(selectedPatternIds = it.selectedPatternIds.toggle(id)) }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    fun onStatusSelected(status: ClothingStatus) {
        _form.update { it.copy(status = status) }
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
        // A failed edit load would save partial data over the item, whatever the user typed since.
        if (state.loadFailed) return
        if (state.isSegmenting || state.isDownloadingModel) return
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
                    purchaseDate = state.purchaseDate?.toString(), // ISO yyyy-MM-dd
                    purchaseLocation = state.purchaseLocation.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    imagePath = state.imagePath,
                    sizeValueId = state.selectedSizeValueId,
                    isFavorite = originalEntity?.isFavorite ?: 0,
                    status = state.status,
                    washStatus = originalEntity?.washStatus ?: WashStatus.Clean,
                    // Use the caption generated this session if available. Fall back to the stored
                    // caption only when the image path hasn't changed (i.e. no new image was picked).
                    // If the user replaced the image, treat the old caption as stale and set null.
                    imageCaption = state.imageCaption
                        ?: if (state.imagePath == originalEntity?.imagePath) originalEntity?.imageCaption else null,
                    colorsCurated = colorsCuratedToSave(state),
                    createdAt = originalEntity?.createdAt ?: clock.now(),
                    updatedAt = clock.now()
                )

                val colors = colorRowsToSave(state)
                val result = if (isEditMode) {
                    clothingRepository.updateItemWithAttributes(
                        item,
                        colors,
                        state.selectedSeasonIds.toList(),
                        state.selectedOccasionIds.toList(),
                        state.selectedMaterialIds.toList(),
                        state.selectedPatternIds.toList(),
                    )
                } else {
                    clothingRepository.insertItemWithAttributes(
                        item,
                        colors,
                        state.selectedSeasonIds.toList(),
                        state.selectedOccasionIds.toList(),
                        state.selectedMaterialIds.toList(),
                        state.selectedPatternIds.toList(),
                    )
                }

                if (result is DataResult.Success) {
                    // Report Quick Add shortcut usage only when the flow originated from it.
                    // Doing this at save (not at background removal) ensures the signal is only
                    // sent when the user actually completes the add, not on every segmentation.
                    if (openCamera) {
                        shortcutUsageReporter.reportUsed("quick_add")
                    }
                    // Best-effort cleanup — failures must not block navigation or show a save error
                    if (isEditMode && originalImagePath != null && originalImagePath != state.imagePath) {
                        runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(originalImagePath!!) } }
                            .onFailure { log.e(it) { "save: failed to delete old image $originalImagePath" } }
                    }
                    val segOrigPath = state.originalSegmentationImagePath
                    if (segOrigPath != null && segOrigPath != originalImagePath) {
                        runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(segOrigPath) } }
                            .onFailure { log.e(it) { "save: failed to delete seg original $segOrigPath" } }
                    }
                    _events.send(ClothingFormEvent.NavigateBack)
                } else {
                    _form.update { it.copy(isSaving = false, errorMessage = CoreRes.string.error_database_query) }
                }
            } catch (e: Exception) {
                log.e(e) { "save clothing item failed" }
                _form.update { it.copy(isSaving = false, errorMessage = CoreRes.string.error_database_query) }
            }
        }
    }

    /**
     * Each selected colour as its measured row when it has one, otherwise a plain user row. After
     * a manual toggle every row becomes user (measurements kept), so a later extraction pass
     * (`replaceAutoColors`) leaves the user's choice alone. Untouched rows are re-saved exactly.
     */
    private fun colorRowsToSave(state: FormState): List<TaggedColor> =
        state.selectedColors.map { color ->
            val row = state.measured[color.id] ?: TaggedColor(color.id, source = ColorSource.USER)
            if (state.colorsEditedByUser) row.copy(source = ColorSource.USER) else row
        }

    /**
     * The stored `colors_curated` flag (#128): set after a manual toggle (clear-all included),
     * cleared when this session's extraction set the selection and it wasn't touched since,
     * otherwise the stored value (false for a new item).
     */
    private fun colorsCuratedToSave(state: FormState): Boolean = when {
        state.colorsEditedByUser -> true
        state.colorsFromExtraction -> false
        else -> originalEntity?.colorsCurated ?: false
    }

    fun cancel() {
        val currentPath = _form.value.imagePath
        val segOrigPath = _form.value.originalSegmentationImagePath
        viewModelScope.launch {
            if (currentPath != null && currentPath != originalImagePath) {
                runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(currentPath) } }
                    .onFailure { log.e(it) { "cancel: failed to delete staged image $currentPath" } }
            }
            // Also clean up the pre-segmentation file if it wasn't the persisted original
            if (segOrigPath != null && segOrigPath != originalImagePath && segOrigPath != currentPath) {
                runCatching { withContext(NonCancellable) { photoProcessor.deleteImage(segOrigPath) } }
                    .onFailure { log.e(it) { "cancel: failed to delete seg original $segOrigPath" } }
            }
            _events.send(ClothingFormEvent.NavigateBack)
        }
    }
}

sealed class ClothingFormEvent {
    object NavigateBack : ClothingFormEvent()
    object OpenImagePicker : ClothingFormEvent()
}
