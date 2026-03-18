package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.WashStatus
import com.closet.core.ui.theme.ClosetTheme
import java.text.NumberFormat
import java.util.*

/**
 * Screen for viewing the details of a specific clothing item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wardrobe_delete_item_title)) },
            text = { Text(stringResource(R.string.wardrobe_delete_item_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(onDeleted = onBackClick)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.wardrobe_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.wardrobe_cancel))
                }
            }
        )
    }

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
                            contentDescription = stringResource(R.string.wardrobe_back)
                        )
                    }
                },
                actions = {
                    val state = uiState
                    if (state is ClothingDetailUiState.Success) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (state.item.isFavorite == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.wardrobe_favorite),
                                tint = if (state.item.isFavorite == 1) Color.Red else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { /* Placeholder for Edit */ }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.wardrobe_edit)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.wardrobe_delete)
                            )
                        }
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
                    ErrorContent(
                        userMessage = stringResource(state.userMessage.resId, *state.userMessage.args),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ClothingDetailUiState.Success -> {
                    ClothingDetailContent(
                        item = state.item,
                        onWashStatusToggle = { viewModel.toggleWashStatus() }
                    )
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
    onWashStatusToggle: () -> Unit,
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

        // Stats Group Card
        StatsGroup(
            wearCount = item.wearCount,
            costPerWear = item.costPerWear
        )

        Spacer(modifier = Modifier.height(16.dp))

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
            
            AssistChip(
                onClick = onWashStatusToggle,
                label = { Text(item.washStatus.label) }
            )
        }
    }
}

/**
 * Card displaying usage statistics like wear count and cost per wear.
 */
@Composable
private fun StatsGroup(
    wearCount: Int,
    costPerWear: Double?,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wardrobe_usage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.wardrobe_worn_times,
                        wearCount,
                        wearCount
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            VerticalDivider(
                modifier = Modifier.height(40.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wardrobe_cost_per_wear),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = if (costPerWear != null) currencyFormatter.format(costPerWear) else "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
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
            DetailRow(
                label = stringResource(R.string.wardrobe_category),
                value = category ?: stringResource(R.string.wardrobe_uncategorized)
            )
            if (subcategory != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                DetailRow(
                    label = stringResource(R.string.wardrobe_subcategory),
                    value = subcategory
                )
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

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun ClothingDetailContentPreview() {
    ClosetTheme {
        Surface {
            ClothingDetailContent(
                item = ClothingItemWithMeta(
                    id = 1L,
                    name = "Vintage Denim Jacket",
                    brand = "Levi's",
                    categoryName = "Outerwear",
                    subcategoryName = "Jackets",
                    imagePath = null,
                    wearCount = 12,
                    purchasePrice = 89.99,
                    status = ClothingStatus.Active,
                    isFavorite = 1,
                    washStatus = WashStatus.Clean
                ),
                onWashStatusToggle = {}
            )
        }
    }
}
