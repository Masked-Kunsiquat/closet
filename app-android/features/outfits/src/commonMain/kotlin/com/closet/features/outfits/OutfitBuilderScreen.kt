package com.closet.features.outfits

import com.closet.core.ui.resources.ic_symbol_arrow_back
import com.closet.core.ui.resources.ic_symbol_add
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.features.outfits.resources.Res
import com.closet.features.outfits.resources.outfits_builder_add_items
import com.closet.features.outfits.resources.outfits_builder_back
import com.closet.features.outfits.resources.outfits_builder_name_hint
import com.closet.features.outfits.resources.outfits_builder_save
import com.closet.features.outfits.resources.outfits_builder_save_error
import com.closet.features.outfits.resources.outfits_builder_title
import com.closet.features.outfits.resources.outfits_builder_title_edit
import com.closet.features.outfits.resources.outfits_builder_update
import com.closet.features.outfits.resources.outfits_builder_update_error
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

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
    viewModel: OutfitBuilderViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(Res.string.outfits_builder_save_error)

    val updateErrorMessage = stringResource(Res.string.outfits_builder_update_error)

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
    resolveImagePath: (String?) -> String?,
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
                            if (uiState.isEditing) Res.string.outfits_builder_title_edit
                            else Res.string.outfits_builder_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.ic_symbol_arrow_back),
                            contentDescription = stringResource(Res.string.outfits_builder_back)
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
                                    if (uiState.isEditing) Res.string.outfits_builder_update
                                    else Res.string.outfits_builder_save
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
                        painter = painterResource(CoreRes.drawable.ic_symbol_add),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.outfits_builder_add_items))
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
                placeholder = { Text(stringResource(Res.string.outfits_builder_name_hint)) },
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
