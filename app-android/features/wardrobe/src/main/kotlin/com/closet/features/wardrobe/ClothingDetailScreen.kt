package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.ClothingItemWithMeta

/**
 * Screen for viewing the details of a specific clothing item.
 *
 * @param onBackClick Callback to navigate back to the previous screen.
 * @param modifier The [Modifier] to be applied to the screen.
 * @param viewModel The [ClothingDetailViewModel] providing state for this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is ClothingDetailUiState.Success -> state.item.name
                        else -> ""
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ClothingDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ClothingDetailUiState.Error -> {
                    val context = LocalContext.current
                    ErrorContent(
                        userMessage = context.getString(state.userMessage.resId, *state.userMessage.args),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ClothingDetailUiState.Success -> {
                    ClothingDetailContent(item = state.item)
                }
            }
        }
    }
}

/**
 * Displays an error message when the item detail fails to load.
 */
@Composable
private fun ErrorContent(
    userMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = userMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Displays the successful content of the clothing item details.
 * TODO: Implement detailed item view in the next step.
 */
@Composable
private fun ClothingDetailContent(
    item: ClothingItemWithMeta,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Details for: ${item.name}", style = MaterialTheme.typography.headlineMedium)
        // More details will be added here in Step 2.2
    }
}
