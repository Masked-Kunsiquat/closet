package com.closet.features.outfits

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Screen for creating a new outfit by selecting items and giving the outfit a name.
 *
 * @param onBack Called when the user presses back or after a successful save.
 * @param onAddItems Called when the user taps "Add Items" — caller navigates to [WardrobePickerScreen].
 */
@Composable
fun OutfitBuilderScreen(
    onBack: () -> Unit,
    onAddItems: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutfitBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.outfits_builder_save_error)

    val updateErrorMessage = stringResource(R.string.outfits_builder_update_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OutfitBuilderEvent.SaveSuccess -> onBack()
                OutfitBuilderEvent.SaveError -> snackbarHostState.showSnackbar(
                    if (uiState.isEditing) updateErrorMessage else errorMessage
                )
            }
        }
    }

    OutfitBuilderContent(
        uiState = uiState,
        resolveImagePath = viewModel::resolveImagePath,
        onBack = onBack,
        onSave = viewModel::save,
        onNameChange = viewModel::updateName,
        onAddItems = onAddItems,
        onRemoveMember = viewModel::removeMember,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitBuilderContent(
    uiState: OutfitBuilderUiState,
    resolveImagePath: (String?) -> java.io.File?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onAddItems: () -> Unit,
    onRemoveMember: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditing) R.string.outfits_builder_title_edit
                            else R.string.outfits_builder_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.outfits_builder_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = uiState.canSave
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(
                                    if (uiState.isEditing) R.string.outfits_builder_update
                                    else R.string.outfits_builder_save
                                )
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onAddItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.outfits_builder_add_items))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(R.string.outfits_builder_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // SWAP POINT: replace OutfitComposition with OutfitCanvas for canvas layout.
            OutfitComposition(
                members = uiState.members,
                onRemoveMember = onRemoveMember,
                resolveImagePath = resolveImagePath,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
