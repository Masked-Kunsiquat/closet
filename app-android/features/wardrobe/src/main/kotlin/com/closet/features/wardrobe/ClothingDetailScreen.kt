package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
 */
@Composable
private fun ClothingDetailContent(
    item: ClothingItemWithMeta,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Image Section
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            AsyncImage(
                model = item.imagePath,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Header Info Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            item.brand?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Details Group Card
        DetailGroup(
            category = item.categoryName,
            subcategory = item.subcategoryName
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance Group (Chips)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { /* Status change handled in next phase */ },
                label = { Text(item.status.label) }
            )
            
            // Note: WashStatus is not in ClothingItemWithMeta, we'll need to update it
            // For now, using Status as a placeholder for the look.
            AssistChip(
                onClick = { /* Toggle wash handled in next phase */ },
                label = { Text("Clean") }, // Placeholder label
                leadingIcon = {
                    // We can add an icon here later if desired
                }
            )
        }
    }
}

/**
 * Card displaying primary metadata like Category and Subcategory.
 */
@Composable
private fun DetailGroup(
    category: String?,
    subcategory: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailRow(label = "Category", value = category ?: "Uncategorized")
            if (subcategory != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                DetailRow(label = "Subcategory", value = subcategory)
            }
        }
    }
}

/**
 * A single row within a detail group.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
