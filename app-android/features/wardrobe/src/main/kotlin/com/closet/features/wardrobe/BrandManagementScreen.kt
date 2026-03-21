package com.closet.features.wardrobe

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.BrandEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandManagementScreen(
    onBack: () -> Unit,
    viewModel: BrandManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAddingBrand by remember { mutableStateOf(false) }
    var newBrandText by remember { mutableStateOf("") }
    var editingBrandId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }

    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                viewModel.saveBrand(null, newBrandText)
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
                                viewModel.saveBrand(brand.id, editText)
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
                        onDeleteClick = { viewModel.requestDelete(brand) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    when (val dialog = uiState.dialog) {
        is BrandManagementDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.brand_management_delete_confirm_title)) },
                text = {
                    Text(stringResource(R.string.brand_management_delete_confirm_message, dialog.brand.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmDelete(dialog.brand.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.brand_management_confirm_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.wardrobe_cancel))
                    }
                }
            )
        }
        is BrandManagementDialog.BlockedDelete -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
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
                    TextButton(onClick = viewModel::dismissDialog) {
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
