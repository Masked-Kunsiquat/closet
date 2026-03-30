package com.closet.features.wardrobe

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.ui.components.ResErrorSnackbarEffect
import com.closet.core.data.model.BrandEntity
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SubcategoryEntity
import java.time.LocalDate

/**
 * Unified screen for adding or editing a clothing item.
 */
@Composable
fun ClothingFormScreen(
    onBackClick: () -> Unit,
    onManageBrands: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClothingFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ClothingFormEvent.NavigateBack -> onBackClick()
            }
        }
    }

    ResErrorSnackbarEffect(
        errorRes = uiState.errorMessage,
        snackbarHostState = snackbarHostState,
        onErrorConsumed = viewModel::onErrorConsumed
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )

    val handleBack = {
        if (uiState.isDirty) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    BackHandler(onBack = handleBack)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.wardrobe_discard_title)) },
            text = { Text(stringResource(R.string.wardrobe_discard_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::cancel) {
                    Text(stringResource(R.string.wardrobe_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.wardrobe_discard_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ClothingFormTopBar(
                isEditMode = uiState.isEditMode,
                canSave = uiState.canSave,
                onBackClick = handleBack,
                onSaveClick = viewModel::save
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            ClothingFormContent(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onBrandQueryChange = viewModel::onBrandQueryChange,
                onBrandSelect = viewModel::onBrandSelected,
                onAddNewBrand = null,
                onManageBrands = onManageBrands,
                onCategorySelect = viewModel::onCategorySelected,
                onSubcategorySelect = viewModel::onSubcategorySelected,
                onSizeSystemSelected = viewModel::onSizeSystemSelected,
                onSizeValueSelected = viewModel::onSizeValueSelected,
                onPriceChange = viewModel::onPriceChange,
                onDateChange = viewModel::onDateChange,
                onLocationChange = viewModel::onLocationChange,
                onNotesChange = viewModel::onNotesChange,
                onImageClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveBackground = viewModel::removeBackground,
                onRevertSegmentation = viewModel::revertSegmentation,
                onColorToggle = viewModel::onColorToggle,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClothingFormTopBar(
    isEditMode: Boolean,
    canSave: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                if (isEditMode) stringResource(R.string.wardrobe_edit_item)
                else stringResource(R.string.wardrobe_add_item)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.wardrobe_back)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSaveClick,
                enabled = canSave
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.wardrobe_save)
                )
            }
        }
    )
}

@Composable
internal fun ClothingFormContent(
    uiState: ClothingFormUiState,
    onNameChange: (String) -> Unit,
    onBrandQueryChange: (String) -> Unit,
    onBrandSelect: (BrandEntity) -> Unit,
    onAddNewBrand: ((String) -> Unit)?,
    onManageBrands: () -> Unit,
    onCategorySelect: (CategoryEntity?) -> Unit,
    onSubcategorySelect: (SubcategoryEntity?) -> Unit,
    onSizeSystemSelected: (Long?) -> Unit,
    onSizeValueSelected: (Long?) -> Unit,
    onPriceChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onImageClick: () -> Unit,
    onRemoveBackground: () -> Unit,
    onRevertSegmentation: () -> Unit,
    onColorToggle: (ColorEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Section
        item {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !uiState.isSegmenting, onClick = onImageClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.imagePath != null) {
                        AsyncImage(
                            model = uiState.imageFile ?: uiState.imagePath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (uiState.isSegmenting) Modifier.alpha(0.4f) else Modifier),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.wardrobe_add_photo),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (uiState.isSegmenting) {
                        CircularProgressIndicator()
                    }
                }

                if (uiState.imageFile != null && !uiState.isSegmenting && !uiState.hasSegmentedImage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRemoveBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wardrobe_remove_background))
                    }
                }

                if (uiState.hasSegmentedImage && !uiState.isSegmenting) {
                    TextButton(
                        onClick = onRevertSegmentation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wardrobe_revert_segmentation))
                    }
                }
            }
        }

        // Basic Info Section
        item {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.wardrobe_field_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.isNameError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BrandAutocompleteField(
                        query = uiState.brandQuery,
                        allBrands = uiState.allBrands,
                        onQueryChange = onBrandQueryChange,
                        onBrandSelect = onBrandSelect,
                        onAddNewBrand = onAddNewBrand
                    )
                }
                IconButton(onClick = onManageBrands) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.brand_management_manage_brands),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Category & Subcategory
        item {
            DropdownSelector(
                selectedItem = uiState.category,
                items = uiState.categories,
                onItemSelect = onCategorySelect,
                label = stringResource(R.string.wardrobe_field_category),
                itemLabel = { it.name }
            )
        }

        item {
            DropdownSelector(
                selectedItem = uiState.subcategory,
                items = uiState.subcategories,
                onItemSelect = onSubcategorySelect,
                label = stringResource(R.string.wardrobe_field_subcategory),
                itemLabel = { it.name },
                enabled = uiState.category != null
            )
        }

        // Size Section
        item {
            SizeSection(
                sizeSystems = uiState.sizeSystems,
                sizeValues = uiState.sizeValues,
                selectedSizeSystemId = uiState.selectedSizeSystemId,
                selectedSizeValueId = uiState.selectedSizeValueId,
                onSizeSystemSelected = onSizeSystemSelected,
                onSizeValueSelected = onSizeValueSelected
            )
        }

        // Color Section
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.wardrobe_section_colors),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ColorSelectionGrid(
                    selectedColors = uiState.selectedColors,
                    allColors = uiState.allColors,
                    onColorToggle = onColorToggle
                )
            }
        }

        item { HorizontalDivider() }

        // Purchase Info Section
        item {
            OutlinedTextField(
                value = uiState.price,
                onValueChange = onPriceChange,
                label = { Text(stringResource(R.string.wardrobe_field_price)) },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )
        }

        item {
            DatePickerField(
                selectedDate = uiState.purchaseDate,
                onDateChange = onDateChange
            )
        }

        item {
            OutlinedTextField(
                value = uiState.purchaseLocation,
                onValueChange = onLocationChange,
                label = { Text(stringResource(R.string.wardrobe_field_location)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        // Notes Section
        item {
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.wardrobe_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
    }
}
