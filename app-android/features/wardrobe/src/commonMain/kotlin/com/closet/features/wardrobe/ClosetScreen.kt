package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_add
import com.closet.core.ui.resources.ic_symbol_check
import com.closet.core.ui.resources.ic_symbol_favorite
import com.closet.core.ui.resources.ic_symbol_favorite_filled
import com.closet.core.ui.resources.ic_symbol_filter_list
import com.closet.core.ui.resources.ic_symbol_push_pin
import com.closet.core.ui.resources.ic_symbol_settings
import com.closet.core.ui.resources.ic_symbol_sort
import com.closet.features.wardrobe.resources.wardrobe_add_item
import com.closet.features.wardrobe.resources.wardrobe_all_items
import com.closet.features.wardrobe.resources.wardrobe_cd_open_filters
import com.closet.features.wardrobe.resources.wardrobe_cd_open_settings
import com.closet.features.wardrobe.resources.wardrobe_cd_sort
import com.closet.features.wardrobe.resources.wardrobe_empty_closet
import com.closet.features.wardrobe.resources.wardrobe_filter_clear_filters
import com.closet.features.wardrobe.resources.wardrobe_filter_empty_results
import com.closet.features.wardrobe.resources.wardrobe_filter_favorites
import com.closet.features.wardrobe.resources.wardrobe_filter_show_archived
import com.closet.features.wardrobe.resources.wardrobe_my_closet
import com.closet.features.wardrobe.resources.wardrobe_pin_category
import com.closet.features.wardrobe.resources.wardrobe_sort_least_worn
import com.closet.features.wardrobe.resources.wardrobe_sort_most_worn
import com.closet.features.wardrobe.resources.wardrobe_sort_name_az
import com.closet.features.wardrobe.resources.wardrobe_sort_name_za
import com.closet.features.wardrobe.resources.wardrobe_sort_newest
import com.closet.features.wardrobe.resources.wardrobe_sort_oldest
import com.closet.features.wardrobe.resources.wardrobe_worn_times
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.*
import com.closet.core.ui.components.ClothingItemCard
import com.closet.core.ui.theme.ClosetTheme
import org.koin.compose.viewmodel.koinViewModel

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
    viewModel: ClosetViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ClosetContent(
        items = uiState.items,
        categories = uiState.categories,
        selectedCategoryId = uiState.selectedCategoryId,
        favoritesOnly = uiState.favoritesOnly,
        showArchived = uiState.showArchived,
        sortOrder = uiState.sortOrder,
        colors = uiState.colors,
        seasons = uiState.seasons,
        occasions = uiState.occasions,
        sizeSystems = uiState.sizeSystems,
        selectedColorIds = uiState.selectedColorIds,
        selectedSeasonIds = uiState.selectedSeasonIds,
        selectedOccasionIds = uiState.selectedOccasionIds,
        selectedSizeSystemIds = uiState.selectedSizeSystemIds,
        activeFilterCount = uiState.activeFilterCount,
        hiddenArchivedCount = uiState.hiddenArchivedCount,
        onCategorySelect = viewModel::selectCategory,
        onToggleFavorites = viewModel::toggleFavoritesOnly,
        onToggleShowArchived = viewModel::toggleShowArchived,
        onSortOrderSelected = viewModel::setSortOrder,
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
    sortOrder: SortOrder,
    colors: List<ColorEntity>,
    seasons: List<SeasonEntity>,
    occasions: List<OccasionEntity>,
    sizeSystems: List<SizeSystemEntity>,
    selectedColorIds: Set<Long>,
    selectedSeasonIds: Set<Long>,
    selectedOccasionIds: Set<Long>,
    selectedSizeSystemIds: Set<Long>,
    activeFilterCount: Int,
    hiddenArchivedCount: Int = 0,
    onCategorySelect: (Long?) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleShowArchived: () -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onToggleColor: (Long) -> Unit,
    onToggleSeason: (Long) -> Unit,
    onToggleOccasion: (Long) -> Unit,
    onToggleSizeSystem: (Long) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onClearAllFilters: () -> Unit,
    resolveImagePath: (String?) -> String?,
    onPinCategory: (Long, String) -> Unit = { _, _ -> },
    onAddItemClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(Res.string.wardrobe_my_closet)) },
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
                                painter = painterResource(CoreRes.drawable.ic_symbol_filter_list),
                                contentDescription = stringResource(Res.string.wardrobe_cd_open_filters)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                painter = painterResource(CoreRes.drawable.ic_symbol_sort),
                                contentDescription = stringResource(Res.string.wardrobe_cd_sort)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(when (order) {
                                            SortOrder.NEWEST     -> Res.string.wardrobe_sort_newest
                                            SortOrder.OLDEST     -> Res.string.wardrobe_sort_oldest
                                            SortOrder.NAME_AZ    -> Res.string.wardrobe_sort_name_az
                                            SortOrder.NAME_ZA    -> Res.string.wardrobe_sort_name_za
                                            SortOrder.MOST_WORN  -> Res.string.wardrobe_sort_most_worn
                                            SortOrder.LEAST_WORN -> Res.string.wardrobe_sort_least_worn
                                        }))
                                    },
                                    onClick = {
                                        onSortOrderSelected(order)
                                        showSortMenu = false
                                    },
                                    trailingIcon = if (order == sortOrder) {
                                        { Icon(painterResource(CoreRes.drawable.ic_symbol_check), contentDescription = null) }
                                    } else null,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.ic_symbol_settings),
                            contentDescription = stringResource(Res.string.wardrobe_cd_open_settings),
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItemClick) {
                Icon(
                    painter = painterResource(CoreRes.drawable.ic_symbol_add),
                    contentDescription = stringResource(Res.string.wardrobe_add_item)
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
                else -> EmptyClosetMessage(
                    hiddenArchivedCount = hiddenArchivedCount,
                    onShowArchived = onToggleShowArchived,
                    modifier = Modifier.weight(1f)
                )
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
                label = { Text(stringResource(Res.string.wardrobe_filter_favorites)) },
                leadingIcon = {
                    Icon(
                        painter = if (favoritesOnly) painterResource(CoreRes.drawable.ic_symbol_favorite_filled) else painterResource(CoreRes.drawable.ic_symbol_favorite),
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
                label = { Text(stringResource(Res.string.wardrobe_all_items)) }
            )
        }
        item(key = "show_archived") {
            FilterChip(
                selected = showArchived,
                onClick = onToggleShowArchived,
                label = { Text(stringResource(Res.string.wardrobe_filter_show_archived)) }
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
                        painter = painterResource(CoreRes.drawable.ic_symbol_push_pin),
                        contentDescription = stringResource(Res.string.wardrobe_pin_category, category.name),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    resolveImagePath: (String?) -> String?,
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
                    Res.plurals.wardrobe_worn_times,
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
 *
 * If [hiddenArchivedCount] > 0, a "Show archived" button is shown so the user can reveal
 * items that were hidden by the Active-only default filter.
 */
@Composable
private fun EmptyClosetMessage(
    hiddenArchivedCount: Int = 0,
    onShowArchived: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.wardrobe_empty_closet))
            if (hiddenArchivedCount > 0) {
                TextButton(onClick = onShowArchived) {
                    Text(stringResource(Res.string.wardrobe_filter_show_archived))
                }
            }
        }
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
                text = stringResource(Res.string.wardrobe_filter_empty_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearFilters) {
                Text(stringResource(Res.string.wardrobe_filter_clear_filters))
            }
        }
    }
}
