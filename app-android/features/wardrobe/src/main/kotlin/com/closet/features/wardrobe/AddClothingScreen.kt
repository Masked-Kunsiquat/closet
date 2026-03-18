package com.closet.features.wardrobe

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.ui.theme.ClosetTheme

/**
 * Screen for adding a new clothing item to the wardrobe.
 *
 * @param onBackClick Callback to navigate back.
 * @param modifier The [Modifier] to be applied to the screen.
 * @param viewModel The [AddClothingViewModel] managing form state.
 */
@Composable
fun AddClothingScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddClothingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImageSelected(uri) }
    )

    AddClothingContent(
        uiState = uiState,
        onNameChange = viewModel::updateName,
        onBrandChange = viewModel::updateBrand,
        onCategorySelect = viewModel::selectCategory,
        onSubcategorySelect = viewModel::selectSubcategory,
        onPhotoClick = {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSaveClick = {
            if (viewModel.validate()) {
                // Save logic will be implemented in Sub-phase 1.6
            }
        },
        onBackClick = onBackClick,
        modifier = modifier
    )
}

/**
 * The content of the Add Clothing screen, decoupled for previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddClothingContent(
    uiState: AddClothingUiState,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onCategorySelect: (CategoryEntity?) -> Unit,
    onSubcategorySelect: (SubcategoryEntity?) -> Unit,
    onPhotoClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSubcategorySheet by remember { mutableStateOf(false) }

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            SelectionSheetContent(
                title = stringResource(R.string.wardrobe_category),
                items = uiState.categories,
                itemLabel = { it.name },
                onItemSelect = {
                    onCategorySelect(it)
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
                onItemSelect = {
                    onSubcategorySelect(it)
                    showSubcategorySheet = false
                }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wardrobe_add_item)) },
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
                        enabled = uiState.canSave
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.wardrobe_save)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                if (uiState.imageFile != null) {
                    AsyncImage(
                        model = uiState.imageFile,
                        contentDescription = stringResource(R.string.wardrobe_clothing_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Optional: Add a "change" label or icon overlay
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
                } else {
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

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.wardrobe_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.isNameError,
                singleLine = true,
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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Category Selection
            ReadOnlyTextField(
                value = uiState.category?.name ?: "",
                label = stringResource(R.string.wardrobe_category),
                onClick = {
                    focusManager.clearFocus()
                    showCategorySheet = true
                }
            )

            if (uiState.category != null && uiState.subcategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                // Subcategory Selection
                ReadOnlyTextField(
                    value = uiState.subcategory?.name ?: "",
                    label = stringResource(R.string.wardrobe_subcategory),
                    onClick = {
                        focusManager.clearFocus()
                        showSubcategorySheet = true
                    }
                )
            }
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clickable(onClick = onClick)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false, // Use disabled colors to signal read-only interaction
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
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
            .padding(bottom = 32.dp) // Extra padding for system bars
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
private fun AddClothingContentPreview() {
    ClosetTheme {
        Surface {
            AddClothingContent(
                uiState = AddClothingUiState(
                    name = "Vintage Jacket",
                    brand = "Levi's",
                    canSave = true,
                    categories = listOf(CategoryEntity(1, "Tops", null, 1))
                ),
                onNameChange = {},
                onBrandChange = {},
                onCategorySelect = {},
                onSubcategorySelect = {},
                onPhotoClick = {},
                onSaveClick = {},
                onBackClick = {}
            )
        }
    }
}
