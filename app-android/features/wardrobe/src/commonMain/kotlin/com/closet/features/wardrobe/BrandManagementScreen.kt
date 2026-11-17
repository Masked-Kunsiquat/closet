package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_add
import com.closet.core.ui.resources.ic_symbol_arrow_back
import com.closet.features.wardrobe.resources.brand_management_add_brand
import com.closet.features.wardrobe.resources.brand_management_confirm_delete
import com.closet.features.wardrobe.resources.brand_management_delete_blocked_message
import com.closet.features.wardrobe.resources.brand_management_delete_blocked_title
import com.closet.features.wardrobe.resources.brand_management_delete_confirm_message
import com.closet.features.wardrobe.resources.brand_management_delete_confirm_title
import com.closet.features.wardrobe.resources.brand_management_edit_hint
import com.closet.features.wardrobe.resources.brand_management_empty
import com.closet.features.wardrobe.resources.brand_management_new_brand_hint
import com.closet.features.wardrobe.resources.brand_management_title
import com.closet.features.wardrobe.resources.wardrobe_back
import com.closet.features.wardrobe.resources.wardrobe_cancel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.BrandEntity
import com.closet.core.ui.components.StringErrorSnackbarEffect
import com.closet.core.ui.theme.ClosetTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BrandManagementScreen(
    onBack: () -> Unit,
    viewModel: BrandManagementViewModel = koinViewModel()
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
    var editorState by remember { mutableStateOf<BrandEditorState>(BrandEditorState.Idle) }

    StringErrorSnackbarEffect(uiState.errorMessage, snackbarHostState, onErrorConsumed)

    // Close the active row only when a pending save completes successfully.
    // On error the row stays open so the user can correct the input.
    LaunchedEffect(uiState.isLoading) {
        val state = editorState
        val isSaving = when (state) {
            is BrandEditorState.Adding -> state.saving
            is BrandEditorState.Editing -> state.saving
            BrandEditorState.Idle -> false
        }
        if (isSaving && !uiState.isLoading) {
            editorState = if (uiState.errorMessage == null) {
                BrandEditorState.Idle
            } else {
                when (state) {
                    is BrandEditorState.Adding -> state.copy(saving = false)
                    is BrandEditorState.Editing -> state.copy(saving = false)
                    BrandEditorState.Idle -> BrandEditorState.Idle
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.brand_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(CoreRes.drawable.ic_symbol_arrow_back), contentDescription = stringResource(Res.string.wardrobe_back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (editorState == BrandEditorState.Idle) {
                FloatingActionButton(onClick = {
                    editorState = BrandEditorState.Adding()
                }) {
                    Icon(painterResource(CoreRes.drawable.ic_symbol_add), contentDescription = stringResource(Res.string.brand_management_add_brand))
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
            if (editorState is BrandEditorState.Adding) {
                val addState = editorState as BrandEditorState.Adding
                item {
                    BrandInputRow(
                        label = stringResource(Res.string.brand_management_new_brand_hint),
                        text = addState.text,
                        onTextChange = { editorState = addState.copy(text = it) },
                        isSaving = addState.saving,
                        onConfirm = {
                            if (addState.text.isNotBlank()) {
                                editorState = addState.copy(saving = true)
                                onSaveBrand(null, addState.text)
                            }
                        },
                        onCancel = { editorState = BrandEditorState.Idle }
                    )
                    HorizontalDivider()
                }
            }

            if (uiState.brands.isEmpty() && editorState !is BrandEditorState.Adding) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.brand_management_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(uiState.brands, key = { it.id }) { brand ->
                val editState = editorState as? BrandEditorState.Editing
                if (editState != null && editState.brandId == brand.id) {
                    BrandInputRow(
                        label = stringResource(Res.string.brand_management_edit_hint),
                        text = editState.text,
                        onTextChange = { editorState = editState.copy(text = it) },
                        isSaving = editState.saving,
                        onConfirm = {
                            if (editState.text.isNotBlank()) {
                                editorState = editState.copy(saving = true)
                                onSaveBrand(brand.id, editState.text)
                            }
                        },
                        onCancel = { editorState = BrandEditorState.Idle }
                    )
                } else {
                    BrandRow(
                        brand = brand,
                        onEditClick = {
                            editorState = BrandEditorState.Editing(brandId = brand.id, text = brand.name)
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
                title = { Text(stringResource(Res.string.brand_management_delete_confirm_title)) },
                text = {
                    Text(stringResource(Res.string.brand_management_delete_confirm_message, dialog.brand.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmDelete(dialog.brand.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(Res.string.brand_management_confirm_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(Res.string.wardrobe_cancel))
                    }
                }
            )
        }
        is BrandManagementDialog.BlockedDelete -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(Res.string.brand_management_delete_blocked_title)) },
                text = {
                    Text(
                        pluralStringResource(
                            Res.plurals.brand_management_delete_blocked_message,
                            dialog.itemCount,
                            dialog.brand.name,
                            dialog.itemCount
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(Res.string.wardrobe_cancel))
                    }
                }
            )
        }
        null -> Unit
    }
}
