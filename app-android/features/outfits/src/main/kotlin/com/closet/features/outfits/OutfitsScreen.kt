package com.closet.features.outfits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.OutfitWithItems
import java.io.File

@Composable
fun OutfitsScreen(
    onCreateOutfit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutfitsViewModel = hiltViewModel()
) {
    val outfits by viewModel.outfits.collectAsStateWithLifecycle()
    val wearingOutfitId by viewModel.wearingOutfitId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val wearSuccess = stringResource(R.string.outfits_wear_success)
    val wearError = stringResource(R.string.outfits_wear_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OutfitsEvent.WearSuccess -> snackbarHostState.showSnackbar(wearSuccess)
                OutfitsEvent.WearError -> snackbarHostState.showSnackbar(wearError)
            }
        }
    }

    OutfitsContent(
        outfits = outfits,
        wearingOutfitId = wearingOutfitId,
        resolveImagePath = viewModel::resolveImagePath,
        onCreateOutfit = onCreateOutfit,
        onWearOutfit = viewModel::wearOutfit,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitsContent(
    outfits: List<OutfitWithItems>,
    wearingOutfitId: Long?,
    resolveImagePath: (String?) -> File?,
    onCreateOutfit: () -> Unit,
    onWearOutfit: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.outfits_gallery_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateOutfit) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.outfits_gallery_create)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (outfits.isEmpty()) {
            OutfitsEmptyState(
                onCreateOutfit = onCreateOutfit,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(outfits, key = { it.outfit.id }) { outfitWithItems ->
                    OutfitCard(
                        outfitWithItems = outfitWithItems,
                        resolveImagePath = resolveImagePath,
                        isWearing = wearingOutfitId == outfitWithItems.outfit.id,
                        wearEnabled = wearingOutfitId == null,
                        onWear = { onWearOutfit(outfitWithItems.outfit.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitCard(
    outfitWithItems: OutfitWithItems,
    resolveImagePath: (String?) -> File?,
    isWearing: Boolean,
    wearEnabled: Boolean,
    onWear: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        OutfitPreview(
            items = outfitWithItems.items,
            resolveImagePath = resolveImagePath,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outfitWithItems.outfit.name
                        ?: stringResource(R.string.outfits_gallery_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        R.string.outfits_gallery_item_count,
                        outfitWithItems.items.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onWear,
                enabled = wearEnabled
            ) {
                if (isWearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.outfits_wear_today),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitsEmptyState(
    onCreateOutfit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.outfits_gallery_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreateOutfit) {
            Text(stringResource(R.string.outfits_gallery_create))
        }
    }
}
