package com.closet.features.wardrobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.ui.theme.ClosetTheme

/**
 * Screen for adding a new clothing item to the wardrobe.
 * 
 * @param onBackClick Callback to navigate back.
 * @param modifier The [Modifier] to be applied to the screen.
 * @param viewModel The [AddClothingViewModel] managing form state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClothingScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddClothingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AddClothingContent(
        uiState = uiState,
        onNameChange = viewModel::updateName,
        onBrandChange = viewModel::updateBrand,
        onSaveClick = {
            if (viewModel.validate()) {
                // Save logic will be implemented in Sub-phase 1.5
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
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

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
                .padding(16.dp)
        ) {
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
                    text = "Name is required", // To be extracted in Sub-phase 1.4
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
                    onDone = { 
                        focusManager.clearFocus()
                        onSaveClick()
                    }
                )
            )
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
                    canSave = true
                ),
                onNameChange = {},
                onBrandChange = {},
                onSaveClick = {},
                onBackClick = {}
            )
        }
    }
}
