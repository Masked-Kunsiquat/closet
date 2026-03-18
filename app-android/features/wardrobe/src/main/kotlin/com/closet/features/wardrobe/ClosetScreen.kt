package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.WashStatus
import com.closet.core.ui.components.ClothingItemCard
import com.closet.core.ui.theme.ClosetTheme

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
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()

    ClosetContent(
        items = items,
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelect = viewModel::selectCategory,
        onAddItemClick = onAddItemClick,
        onItemClick = onItemClick,
        modifier = modifier
    )
}

/**
 * Content of the Closet screen, decoupled from ViewModel for previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClosetContent(
    items: List<ClothingItemWithMeta>,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onCategorySelect: (Long?) -> Unit,
    onAddItemClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wardrobe_my_closet)) }
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
                onCategorySelect = onCategorySelect
            )
            
            if (items.isEmpty()) {
                EmptyClosetMessage(modifier = Modifier.weight(1f))
            } else {
                ClosetGrid(
                    items = items,
                    onItemClick = onItemClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A horizontal row of filter chips for categories.
 */
@Composable
private fun CategoryFilterRow(
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
                label = { Text(stringResource(R.string.wardrobe_all_items)) }
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

/**
 * A vertical grid displaying clothing items.
 * 
 * @param items The list of clothing items to display.
 * @param onItemClick Callback for item interaction.
 * @param modifier The [Modifier] to be applied to the grid.
 */
@Composable
private fun ClosetGrid(
    items: List<ClothingItemWithMeta>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { itemWithMeta ->
            ClothingItemCard(
                name = itemWithMeta.name,
                imagePath = itemWithMeta.imagePath,
                subtitle = pluralStringResource(
                    R.plurals.wardrobe_worn_times,
                    itemWithMeta.wearCount,
                    itemWithMeta.wearCount
                ),
                onClick = { onItemClick(itemWithMeta.id) }
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

@Preview(showBackground = true, name = "Items - Light")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Items - Dark")
@Composable
private fun ClosetScreenPreview() {
    ClosetTheme {
        Surface {
            ClosetContent(
                items = listOf(
                    ClothingItemWithMeta(
                        id = 1,
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
                    ClothingItemWithMeta(
                        id = 2,
                        name = "White Linen Shirt",
                        brand = "Uniqlo",
                        categoryName = "Tops",
                        subcategoryName = "Shirts",
                        imagePath = null,
                        wearCount = 5,
                        purchasePrice = 29.90,
                        status = ClothingStatus.Active,
                        isFavorite = 0,
                        washStatus = WashStatus.Dirty
                    )
                ),
                categories = listOf(
                    CategoryEntity(id = 1, name = "Tops", sortOrder = 1),
                    CategoryEntity(id = 2, name = "Pants", sortOrder = 2),
                    CategoryEntity(id = 3, name = "Outerwear", sortOrder = 3)
                ),
                selectedCategoryId = null,
                onCategorySelect = {},
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
                onCategorySelect = {},
                onAddItemClick = {},
                onItemClick = {}
            )
        }
    }
}
