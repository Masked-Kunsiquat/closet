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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.ClothingItemWithMeta
import com.closet.core.ui.components.ClothingItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

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

@Composable
private fun EmptyClosetMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(stringResource(R.string.wardrobe_empty_closet))
    }
}
