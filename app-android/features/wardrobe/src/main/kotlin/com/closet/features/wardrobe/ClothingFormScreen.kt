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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
            viewModel.onErrorConsumed()
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
            title = { Text(stringResource(R.string.wardrobe_discard_title)) },
            text = { Text(stringResource(R.string.wardrobe_discard_message)) },
            confirmButton = {
                TextButton(onClick = onBackClick) {
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
                onNameChange = viewModel::updateName,
                onBrandChange = viewModel::updateBrand,
                onCategorySelect = viewModel::selectCategory,
                onSubcategorySelect = viewModel::selectSubcategory,
                onPriceChange = viewModel::updatePrice,
                onDateChange = viewModel::updatePurchaseDate,
                onLocationChange = viewModel::updatePurchaseLocation,
                onNotesChange = viewModel::updateNotes,
                onImageClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onColorToggle = viewModel::toggleColor,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClothingFormTopBar(
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
private fun ClothingFormContent(
    uiState: ClothingFormUiState,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onCategorySelect: (CategoryEntity?) -> Unit,
    onSubcategorySelect: (SubcategoryEntity?) -> Unit,
    onPriceChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onImageClick: () -> Unit,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onImageClick),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.imagePath != null) {
                    AsyncImage(
                        model = uiState.imageFile ?: uiState.imagePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
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
            OutlinedTextField(
                value = uiState.brand,
                onValueChange = onBrandChange,
                label = { Text(stringResource(R.string.wardrobe_field_brand)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        // Category & Subcategory
        item {
            CategoryDropdown(
                selectedCategory = uiState.category,
                categories = uiState.categories,
                onCategorySelect = onCategorySelect
            )
        }

        item {
            SubcategoryDropdown(
                selectedSubcategory = uiState.subcategory,
                subcategories = uiState.subcategories,
                onSubcategorySelect = onSubcategorySelect,
                enabled = uiState.category != null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: CategoryEntity?,
    categories: List<CategoryEntity>,
    onCategorySelect: (CategoryEntity?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.wardrobe_field_category)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        // Simple overlay to handle click since OutlinedTextField readOnly doesn't trigger onClick easily
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            categories.forEach { category ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelect(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubcategoryDropdown(
    selectedSubcategory: SubcategoryEntity?,
    subcategories: List<SubcategoryEntity>,
    onSubcategorySelect: (SubcategoryEntity?) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedSubcategory?.name ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.wardrobe_field_subcategory)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            subcategories.forEach { sub ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(sub.name) },
                    onClick = {
                        onSubcategorySelect(sub)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSelectionGrid(
    selectedColors: List<ColorEntity>,
    allColors: List<ColorEntity>,
    onColorToggle: (ColorEntity) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        items(allColors) { color ->
            val isSelected = selectedColors.any { it.id == color.id }
            val hexColor = try { Color(android.graphics.Color.parseColor(color.hex)) } catch(_: Exception) { Color.Gray }
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(hexColor)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onColorToggle(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isColorDark(hexColor)) Color.White else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    selectedDate: LocalDate?,
    onDateChange: (LocalDate?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    )

    OutlinedTextField(
        value = selectedDate?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.wardrobe_field_purchase_date)) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        onDateChange(date)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.wardrobe_date_confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDateChange(null)
                        showDatePicker = false
                    }) {
                        Text(stringResource(R.string.wardrobe_date_clear))
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.wardrobe_date_cancel))
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun isColorDark(color: Color): Boolean {
    val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return luminance < 0.5
}
