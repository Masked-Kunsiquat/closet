package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.*
import com.closet.core.ui.components.ClothingItemCard
import com.closet.core.ui.theme.ClosetTheme
import java.io.File

/**
 * The main screen for browsing the wardrobe (Closet).
 * Displays a grid of clothing items with their wear counts and category metadata.
 * 
 * @param modifier The [Modifier] to be applied to the screen.
 * @param onAddItemClick Callback invoked when the add button is tapped.
 * @param onItemClick Callback invoked when a clothing item is tapped, passing its ID.
 * @param viewModel The [ClosetViewModel] providing state for this screen.
 */
@Composable
fun ClosetScreen(
    onAddItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit,
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ClosetContent(
        items = uiState.items,
        categories = uiState.categories,
        selectedCategoryId = uiState.selectedCategoryId,
        favoritesOnly = uiState.favoritesOnly,
        showArchived = uiState.showArchived,
        colors = uiState.colors,
        seasons = uiState.seasons,
        occasions = uiState.occasions,
        sizeSystems = uiState.sizeSystems,
        selectedColorIds = uiState.selectedColorIds,
        selectedSeasonIds = uiState.selectedSeasonIds,
        selectedOccasionIds = uiState.selectedOccasionIds,
        selectedSizeSystemIds = uiState.selectedSizeSystemIds,
        activeFilterCount = uiState.activeFilterCount,
        onCategorySelect = viewModel::selectCategory,
        onToggleFavorites = viewModel::toggleFavoritesOnly,
        onToggleShowArchived = viewModel::toggleShowArchived,
        onToggleColor = viewModel::toggleColorFilter,
        onToggleSeason = viewModel::toggleSeasonFilter,
        onToggleOccasion = viewModel::toggleOccasionFilter,
        onToggleSizeSystem = viewModel::toggleSizeSystemFilter,
        onClearAdvancedFilters = viewModel::clearAdvancedFilters,
        onClearAllFilters = viewModel::clearAllFilters,
        resolveImagePath = viewModel::resolveImagePath,
        onPinCategory = viewModel::pinCategoryShortcut,
        onAddItemClick = onAddItemClick,
        onItemClick = onItemClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier
    )
}

/**
 * Content of the Closet screen, decoupled from ViewModel for previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClosetContent(
    items: List<ClothingItemDetail>,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    favoritesOnly: Boolean,
    showArchived: Boolean,
    colors: List<ColorEntity>,
    seasons: List<SeasonEntity>,
    occasions: List<OccasionEntity>,
    sizeSystems: List<SizeSystemEntity>,
    selectedColorIds: Set<Long>,
    selectedSeasonIds: Set<Long>,
    selectedOccasionIds: Set<Long>,
    selectedSizeSystemIds: Set<Long>,
    activeFilterCount: Int,
    onCategorySelect: (Long?) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleShowArchived: () -> Unit,
    onToggleColor: (Long) -> Unit,
    onToggleSeason: (Long) -> Unit,
    onToggleOccasion: (Long) -> Unit,
    onToggleSizeSystem: (Long) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onClearAllFilters: () -> Unit,
    resolveImagePath: (String?) -> File?,
    onPinCategory: (Long, String) -> Unit = { _, _ -> },
    onAddItemClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFilterPanel by remember { mutableStateOf(false) }

    if (showFilterPanel) {
        FilterPanel(
            colors = colors,
            seasons = seasons,
            occasions = occasions,
            sizeSystems = sizeSystems,
            selectedColorIds = selectedColorIds,
            selectedSeasonIds = selectedSeasonIds,
            selectedOccasionIds = selectedOccasionIds,
            selectedSizeSystemIds = selectedSizeSystemIds,
            onToggleColor = onToggleColor,
            onToggleSeason = onToggleSeason,
            onToggleOccasion = onToggleOccasion,
            onToggleSizeSystem = onToggleSizeSystem,
            onClearAll = onClearAdvancedFilters,
            onDismiss = { showFilterPanel = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wardrobe_my_closet)) },
                actions = {
                    IconButton(onClick = { showFilterPanel = true }) {
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge { Text(activeFilterCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.wardrobe_cd_open_filters)
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.wardrobe_cd_open_settings),
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItemClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.wardrobe_add_item)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            CategoryFilterRow(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                favoritesOnly = favoritesOnly,
                showArchived = showArchived,
                onCategorySelect = onCategorySelect,
                onToggleFavorites = onToggleFavorites,
                onToggleShowArchived = onToggleShowArchived,
                onPinCategory = onPinCategory,
            )
            
            val isFiltered = activeFilterCount > 0 || selectedCategoryId != null
            when {
                items.isNotEmpty() -> ClosetGrid(
                    items = items,
                    onItemClick = onItemClick,
                    resolveImagePath = resolveImagePath,
                    modifier = Modifier.weight(1f)
                )
                isFiltered -> FilteredEmptyMessage(
                    onClearFilters = onClearAllFilters,
                    modifier = Modifier.weight(1f)
                )
                else -> EmptyClosetMessage(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Horizontally scrollable row of filter chips.
 *
 * Order: Favorites shortcut → All → per-category chips.
 * The Favorites chip is a standalone toggle independent of the category filter.
 */
@Composable
private fun CategoryFilterRow(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    favoritesOnly: Boolean,
    showArchived: Boolean,
    onCategorySelect: (Long?) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleShowArchived: () -> Unit,
    onPinCategory: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "favorites") {
            FilterChip(
                selected = favoritesOnly,
                onClick = onToggleFavorites,
                label = { Text(stringResource(R.string.wardrobe_filter_favorites)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
        item(key = "all") {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelect(null) },
                label = { Text(stringResource(R.string.wardrobe_all_items)) }
            )
        }
        items(categories, key = { it.id }) { category ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = { onCategorySelect(category.id) },
                    label = { Text(category.name) }
                )
                IconButton(
                    onClick = { onPinCategory(category.id, category.name) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.wardrobe_pin_category, category.name),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "show_archived") {
            FilterChip(
                selected = showArchived,
                onClick = onToggleShowArchived,
                label = { Text(stringResource(R.string.wardrobe_filter_show_archived)) }
            )
        }
    }
}

/**
 * A vertical grid displaying clothing items.
 * 
 * @param items The list of clothing items to display.
 * @param onItemClick Callback for item interaction.
 * @param resolveImagePath Function to resolve relative paths to absolute files.
 * @param modifier The [Modifier] to be applied to the grid.
 */
@Composable
private fun ClosetGrid(
    items: List<ClothingItemDetail>,
    onItemClick: (Long) -> Unit,
    resolveImagePath: (String?) -> File?,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(items, key = { it.item.id }) { itemDetail ->
            ClothingItemCard(
                name = itemDetail.item.name,
                imageModel = resolveImagePath(itemDetail.item.imagePath),
                subtitle = pluralStringResource(
                    R.plurals.wardrobe_worn_times,
                    itemDetail.wearCount,
                    itemDetail.wearCount
                ),
                onClick = { onItemClick(itemDetail.item.id) }
            )
        }
    }
}

/**
 * Message displayed when the wardrobe contains no items.
 */
@Composable
private fun EmptyClosetMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(stringResource(R.string.wardrobe_empty_closet))
    }
}

/**
 * Empty state shown when filters are active but no items match.
 * Provides a "Clear filters" CTA that resets all active filters.
 */
@Composable
private fun FilteredEmptyMessage(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wardrobe_filter_empty_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.wardrobe_filter_clear_filters))
            }
        }
    }
}

@Preview(showBackground = true, name = "Items - Light")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Items - Dark")
@Composable
private fun ClosetScreenPreview() {
    ClosetTheme {
        Surface {
            ClosetContent(
                items = listOf(
                    ClothingItemDetail(
                        item = ClothingItemEntity(
                            id = 1,
                            name = "Vintage Denim Jacket",
                            brandId = 1,
                            categoryId = 1,
                            status = ClothingStatus.Active,
                            isFavorite = 1,
                            washStatus = WashStatus.Clean
                        ),
                        wearCount = 12,
                        brand = com.closet.core.data.model.BrandEntity(id = 1, name = "Levi's", normalizedName = "levi's"),
                        category = CategoryEntity(id = 1, name = "Outerwear", sortOrder = 3),
                        subcategory = null,
                        sizeValue = null,
                        colors = emptyList(),
                        materials = emptyList(),
                        seasons = emptyList(),
                        occasions = emptyList(),
                        patterns = emptyList()
                    )
                ),
                categories = listOf(
                    CategoryEntity(id = 1, name = "Tops", sortOrder = 1),
                    CategoryEntity(id = 2, name = "Pants", sortOrder = 2),
                    CategoryEntity(id = 3, name = "Outerwear", sortOrder = 3)
                ),
                selectedCategoryId = null,
                favoritesOnly = false,
                showArchived = false,
                colors = emptyList(),
                seasons = emptyList(),
                occasions = emptyList(),
                sizeSystems = emptyList(),
                selectedColorIds = emptySet(),
                selectedSeasonIds = emptySet(),
                selectedOccasionIds = emptySet(),
                selectedSizeSystemIds = emptySet(),
                activeFilterCount = 0,
                onCategorySelect = {},
                onToggleFavorites = {},
                onToggleShowArchived = {},
                onToggleColor = {},
                onToggleSeason = {},
                onToggleOccasion = {},
                onToggleSizeSystem = {},
                onClearAdvancedFilters = {},
                onClearAllFilters = {},
                resolveImagePath = { null },
                onAddItemClick = {},
                onItemClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun ClosetScreenEmptyPreview() {
    ClosetTheme {
        Surface {
            ClosetContent(
                items = emptyList(),
                categories = emptyList(),
                selectedCategoryId = null,
                favoritesOnly = false,
                showArchived = false,
                colors = emptyList(),
                seasons = emptyList(),
                occasions = emptyList(),
                sizeSystems = emptyList(),
                selectedColorIds = emptySet(),
                selectedSeasonIds = emptySet(),
                selectedOccasionIds = emptySet(),
                selectedSizeSystemIds = emptySet(),
                activeFilterCount = 0,
                onCategorySelect = {},
                onToggleFavorites = {},
                onToggleShowArchived = {},
                onToggleColor = {},
                onToggleSeason = {},
                onToggleOccasion = {},
                onToggleSizeSystem = {},
                onClearAdvancedFilters = {},
                onClearAllFilters = {},
                resolveImagePath = { null },
                onAddItemClick = {},
                onItemClick = {}
            )
        }
    }
}
