package com.closet.features.outfits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.SeasonEntity
import com.closet.core.ui.components.ClothingItemCard
import java.io.File

/**
 * Full-screen wardrobe picker for selecting items to add to an outfit.
 * Navigates back with the selected item IDs via [onConfirm].
 */
@Composable
fun WardrobePickerScreen(
    onBack: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WardrobePickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WardrobePickerContent(
        uiState = uiState,
        resolveImagePath = viewModel::resolveImagePath,
        onCategorySelect = viewModel::selectCategory,
        onSeasonSelect = viewModel::selectSeason,
        onToggleItem = viewModel::toggleItem,
        onBack = onBack,
        onConfirm = { onConfirm(uiState.selectedItemIds.toList()) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardrobePickerContent(
    uiState: WardrobePickerUiState,
    resolveImagePath: (String?) -> File?,
    onCategorySelect: (Long?) -> Unit,
    onSeasonSelect: (Long?) -> Unit,
    onToggleItem: (Long) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = uiState.selectedItemIds.size
    val confirmLabel = if (selectedCount > 0) {
        stringResource(R.string.outfits_picker_done_count, selectedCount)
    } else {
        stringResource(R.string.outfits_picker_done)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.outfits_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.outfits_picker_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onConfirm,
                        enabled = selectedCount > 0
                    ) {
                        Text(confirmLabel)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PickerCategoryRow(
                categories = uiState.categories,
                selectedCategoryId = uiState.selectedCategoryId,
                onCategorySelect = onCategorySelect
            )
            PickerSeasonRow(
                seasons = uiState.seasons,
                selectedSeasonId = uiState.selectedSeasonId,
                onSeasonSelect = onSeasonSelect
            )
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                PickerEmptyState(modifier = Modifier.weight(1f))
            } else {
                PickerGrid(
                    items = uiState.items,
                    selectedItemIds = uiState.selectedItemIds,
                    resolveImagePath = resolveImagePath,
                    onToggleItem = onToggleItem,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PickerCategoryRow(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onCategorySelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelect(null) },
                label = { Text(stringResource(R.string.outfits_picker_all_categories)) }
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelect(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
private fun PickerSeasonRow(
    seasons: List<SeasonEntity>,
    selectedSeasonId: Long?,
    onSeasonSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedSeasonId == null,
                onClick = { onSeasonSelect(null) },
                label = { Text(stringResource(R.string.outfits_picker_all_seasons)) }
            )
        }
        items(seasons, key = { it.id }) { season ->
            FilterChip(
                selected = selectedSeasonId == season.id,
                onClick = { onSeasonSelect(season.id) },
                label = { Text(season.name) }
            )
        }
    }
}

@Composable
private fun PickerGrid(
    items: List<ClothingItemDetail>,
    selectedItemIds: Set<Long>,
    resolveImagePath: (String?) -> File?,
    onToggleItem: (Long) -> Unit,
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
            val isSelected = itemDetail.item.id in selectedItemIds
            SelectableItemCard(
                item = itemDetail,
                isSelected = isSelected,
                imageModel = resolveImagePath(itemDetail.item.imagePath),
                onToggle = { onToggleItem(itemDetail.item.id) }
            )
        }
    }
}

@Composable
private fun SelectableItemCard(
    item: ClothingItemDetail,
    isSelected: Boolean,
    imageModel: Any?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        ClothingItemCard(
            name = item.item.name,
            imageModel = imageModel,
            subtitle = item.category?.name ?: "",
            onClick = onToggle
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.outfits_picker_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun PickerEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.outfits_picker_empty))
    }
}
