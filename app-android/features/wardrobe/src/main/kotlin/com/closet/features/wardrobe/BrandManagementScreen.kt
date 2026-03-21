package com.closet.features.wardrobe

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.BrandEntity
import com.closet.core.ui.theme.ClosetTheme

@Composable
fun BrandManagementScreen(
    onBack: () -> Unit,
    viewModel: BrandManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BrandManagementContent(
        uiState = uiState,
        onBack = onBack,
        onSaveBrand = viewModel::saveBrand,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDialog = viewModel::dismissDialog,
        onErrorConsumed = viewModel::onErrorConsumed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrandManagementContent(
    uiState: BrandManagementUiState,
    onBack: () -> Unit,
    onSaveBrand: (id: Long?, name: String) -> Unit,
    onRequestDelete: (BrandEntity) -> Unit,
    onConfirmDelete: (Long) -> Unit,
    onDismissDialog: () -> Unit,
    onErrorConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var isAddingBrand by remember { mutableStateOf(false) }
    var newBrandText by remember { mutableStateOf("") }
    var editingBrandId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }

    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.brand_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wardrobe_back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isAddingBrand) {
                FloatingActionButton(onClick = {
                    isAddingBrand = true
                    newBrandText = ""
                    editingBrandId = null
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.brand_management_add_brand))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (isAddingBrand) {
                item {
                    AddBrandRow(
                        text = newBrandText,
                        onTextChange = { newBrandText = it },
                        onConfirm = {
                            if (newBrandText.isNotBlank()) {
                                onSaveBrand(null, newBrandText)
                                isAddingBrand = false
                                newBrandText = ""
                            }
                        },
                        onCancel = {
                            isAddingBrand = false
                            newBrandText = ""
                        }
                    )
                    HorizontalDivider()
                }
            }

            if (uiState.brands.isEmpty() && !isAddingBrand) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.brand_management_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(uiState.brands, key = { it.id }) { brand ->
                if (editingBrandId == brand.id) {
                    EditBrandRow(
                        text = editText,
                        onTextChange = { editText = it },
                        onConfirm = {
                            if (editText.isNotBlank()) {
                                onSaveBrand(brand.id, editText)
                                editingBrandId = null
                            }
                        },
                        onCancel = { editingBrandId = null }
                    )
                } else {
                    BrandRow(
                        brand = brand,
                        onEditClick = {
                            editingBrandId = brand.id
                            editText = brand.name
                            isAddingBrand = false
                        },
                        onDeleteClick = { onRequestDelete(brand) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    when (val dialog = uiState.dialog) {
        is BrandManagementDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(R.string.brand_management_delete_confirm_title)) },
                text = {
                    Text(stringResource(R.string.brand_management_delete_confirm_message, dialog.brand.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmDelete(dialog.brand.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.brand_management_confirm_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(R.string.wardrobe_cancel))
                    }
                }
            )
        }
        is BrandManagementDialog.BlockedDelete -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(R.string.brand_management_delete_blocked_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.brand_management_delete_blocked_message,
                            dialog.brand.name,
                            dialog.itemCount
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(R.string.wardrobe_cancel))
                    }
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun BrandRow(
    brand: BrandEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(brand.name) },
        trailingContent = {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.wardrobe_edit))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.wardrobe_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun AddBrandRow(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.brand_management_new_brand_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        IconButton(onClick = onConfirm, enabled = text.isNotBlank()) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.wardrobe_save))
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wardrobe_cancel))
        }
    }
}

@Composable
private fun EditBrandRow(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.brand_management_edit_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() })
        )
        IconButton(onClick = onConfirm, enabled = text.isNotBlank()) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.wardrobe_save))
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wardrobe_cancel))
        }
    }
}

// --- Previews ---

private val previewBrands = listOf(
    BrandEntity(id = 1, name = "Nike"),
    BrandEntity(id = 2, name = "Adidas"),
    BrandEntity(id = 3, name = "Levi's"),
    BrandEntity(id = 4, name = "Uniqlo"),
    BrandEntity(id = 5, name = "Zara"),
)

@Preview(showBackground = true, name = "Brand List - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Brand List - Dark")
@Composable
private fun BrandManagementContentPreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(brands = previewBrands),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun BrandManagementEmptyPreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(brands = emptyList()),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Confirm Delete Dialog")
@Composable
private fun BrandManagementConfirmDeletePreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(
                    brands = previewBrands,
                    dialog = BrandManagementDialog.ConfirmDelete(previewBrands[0])
                ),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Blocked Delete Dialog")
@Composable
private fun BrandManagementBlockedDeletePreview() {
    ClosetTheme {
        Surface {
            BrandManagementContent(
                uiState = BrandManagementUiState(
                    brands = previewBrands,
                    dialog = BrandManagementDialog.BlockedDelete(previewBrands[1], itemCount = 7)
                ),
                onBack = {},
                onSaveBrand = { _, _ -> },
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDialog = {},
                onErrorConsumed = {}
            )
        }
    }
}
