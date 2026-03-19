package com.closet.features.wardrobe

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ColorEntity
import com.closet.core.ui.theme.ClosetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unified screen for adding or editing a clothing item.
 */
@Composable
fun ClothingFormScreen(
    onBackClick: () -> Unit,
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

    uiState.errorMessage?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
        }
    }

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
            title = { Text(stringResource(R.string.wardrobe_discard_changes_title)) },
            text = { Text(stringResource(R.string.wardrobe_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = onBackClick) {
                    Text(stringResource(R.string.wardrobe_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.wardrobe_cancel))
                }
            }
        )
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ClothingFormContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onNameChange = viewModel::updateName,
            onBrandChange = viewModel::updateBrand,
            onCategorySelect = viewModel::selectCategory,
            onSubcategorySelect = viewModel::selectSubcategory,
            onPriceChange = viewModel::updatePrice,
            onDateChange = viewModel::updatePurchaseDate,
            onLocationChange = viewModel::updatePurchaseLocation,
            onNotesChange = viewModel::updateNotes,
            onColorsChange = viewModel::updateColors,
            onPhotoClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSaveClick = viewModel::save,
            onBackClick = handleBack,
            modifier = modifier
        )
    }
}

/**
 * The content of the Clothing form screen, decoupled for previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClothingFormContent(
    uiState: ClothingFormUiState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onCategorySelect: (CategoryEntity?) -> Unit,
    onSubcategorySelect: (com.closet.core.data.model.SubcategoryEntity?) -> Unit,
    onPriceChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onColorsChange: (List<Long>) -> Unit,
    onPhotoClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSubcategorySheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            SelectionSheetContent(
                title = stringResource(R.string.wardrobe_category),
                items = uiState.categories,
                itemLabel = { it.name },
                onItemSelect = { cat ->
                    onCategorySelect(cat)
                    showCategorySheet = false
                }
            )
        }
    }

    if (showSubcategorySheet) {
        ModalBottomSheet(onDismissRequest = { showSubcategorySheet = false }) {
            SelectionSheetContent(
                title = stringResource(R.string.wardrobe_subcategory),
                items = uiState.subcategories,
                itemLabel = { it.name },
                onItemSelect = { sub ->
                    onSubcategorySelect(sub)
                    showSubcategorySheet = false
                }
            )
        }
    }

    if (showColorSheet) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_colors),
            items = uiState.allColors.map { color ->
                MultiSelectItem(
                    id = color.id,
                    label = color.name,
                    original = color,
                    colorHex = color.hex
                )
            },
            selectedIds = uiState.selectedColors.map { it.id }.toSet(),
            onDismiss = { showColorSheet = false },
            onConfirm = { ids ->
                onColorsChange(ids)
                showColorSheet = false
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.purchaseDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis?.let { ms ->
                        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    onDateChange(selectedDate)
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.wardrobe_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.wardrobe_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.isEditMode) stringResource(R.string.wardrobe_edit) 
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
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onSaveClick,
                            enabled = uiState.canSave
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.wardrobe_save)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo Selection / Preview
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                uiState.imageFile?.let { imgFile ->
                    AsyncImage(
                        model = imgFile,
                        contentDescription = stringResource(R.string.wardrobe_clothing_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.wardrobe_change_photo),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } ?: run {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.wardrobe_add_photo),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Fields
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.wardrobe_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.isNameError,
                singleLine = true,
                enabled = !uiState.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            if (uiState.isNameError) {
                Text(
                    text = stringResource(R.string.wardrobe_error_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.brand,
                onValueChange = onBrandChange,
                label = { Text(stringResource(R.string.wardrobe_brand)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category & Subcategory
            ReadOnlyTextField(
                value = uiState.category?.name ?: "",
                label = stringResource(R.string.wardrobe_category),
                enabled = !uiState.isSaving,
                onClick = {
                    focusManager.clearFocus()
                    showCategorySheet = true
                }
            )

            if (uiState.category != null && uiState.subcategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                ReadOnlyTextField(
                    value = uiState.subcategory?.name ?: "",
                    label = stringResource(R.string.wardrobe_subcategory),
                    enabled = !uiState.isSaving,
                    onClick = {
                        focusManager.clearFocus()
                        showSubcategorySheet = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Colors Section
            ReadOnlyTextField(
                value = if (uiState.selectedColors.isEmpty()) "" else " ", // Placeholder for height
                label = stringResource(R.string.wardrobe_colors),
                enabled = !uiState.isSaving,
                onClick = {
                    focusManager.clearFocus()
                    showColorSheet = true
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                },
                content = {
                    if (uiState.selectedColors.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.selectedColors) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            color.hex?.let { cHex -> Color(cHex.toColorInt()) }
                                                ?: MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Extended Metadata
            OutlinedTextField(
                value = uiState.price,
                onValueChange = onPriceChange,
                label = { Text(stringResource(R.string.wardrobe_purchase_price)) },
                placeholder = { Text(stringResource(R.string.wardrobe_price_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving,
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            ReadOnlyTextField(
                value = uiState.formattedDate ?: "",
                label = stringResource(R.string.wardrobe_purchase_date),
                enabled = !uiState.isSaving,
                trailingIcon = {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
                },
                onClick = {
                    focusManager.clearFocus()
                    showDatePicker = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.purchaseLocation,
                onValueChange = onLocationChange,
                label = { Text(stringResource(R.string.wardrobe_purchase_location)) },
                placeholder = { Text(stringResource(R.string.wardrobe_location_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.wardrobe_notes)) },
                placeholder = { Text(stringResource(R.string.wardrobe_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !uiState.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
        }
    }
}

/**
 * A text field that looks editable but triggers an action instead.
 */
@Composable
private fun ReadOnlyTextField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier.clickable(enabled = enabled, onClick = onClick)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledBorderColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                disabledLabelColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledLeadingIconColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledTrailingIconColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        )
        if (content != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = if (leadingIcon != null) 52.dp else 16.dp, end = if (trailingIcon != null) 52.dp else 16.dp, top = 8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Common content for selection bottom sheets.
 */
@Composable
private fun <T> SelectionSheetContent(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        LazyColumn {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(itemLabel(item)) },
                    modifier = Modifier.clickable { onItemSelect(item) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClothingFormContentPreview() {
    ClosetTheme {
        Surface {
            ClothingFormContent(
                uiState = ClothingFormUiState(
                    name = "Vintage Jacket",
                    brand = "Levi's",
                    canSave = true,
                    categories = listOf(CategoryEntity(1, "Tops", null, 1)),
                    selectedColors = listOf(
                        ColorEntity(1, "Red", "#FF0000"),
                        ColorEntity(2, "Blue", "#0000FF")
                    )
                ),
                snackbarHostState = remember { SnackbarHostState() },
                onNameChange = {},
                onBrandChange = {},
                onCategorySelect = {},
                onSubcategorySelect = {},
                onPriceChange = {},
                onDateChange = {},
                onLocationChange = {},
                onNotesChange = {},
                onColorsChange = {},
                onPhotoClick = {},
                onSaveClick = {},
                onBackClick = {}
            )
        }
    }
}
