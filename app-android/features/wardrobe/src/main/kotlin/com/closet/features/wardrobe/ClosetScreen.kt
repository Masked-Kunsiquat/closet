package com.closet.features.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * @param onItemClick Callback invoked when a clothing item is tapped, passing its ID.
 * @param viewModel The [ClosetViewModel] providing state for this screen.
 */
@Composable
fun ClosetScreen(
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    ClosetContent(
        items = items,
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
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wardrobe_my_closet)) }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyClosetMessage(modifier = Modifier.padding(padding))
        } else {
            ClosetGrid(
                items = items,
                onItemClick = onItemClick,
                modifier = Modifier.padding(padding)
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
                    ),
                    ClothingItemWithMeta(
                        id = 3,
                        name = "Black Chinos",
                        brand = "Everlane",
                        categoryName = "Pants",
                        subcategoryName = "Chinos",
                        imagePath = null,
                        wearCount = 20,
                        purchasePrice = 68.00,
                        status = ClothingStatus.Active,
                        isFavorite = 1,
                        washStatus = WashStatus.Clean
                    )
                ),
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
                onItemClick = {}
            )
        }
    }
}
