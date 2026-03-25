package com.closet.features.outfits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.data.model.OutfitWithItems
import java.io.File

@Composable
fun OutfitsScreen(
    onCreateOutfit: () -> Unit,
    onGetSuggestions: () -> Unit,
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
        onGetSuggestions = onGetSuggestions,
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
    onGetSuggestions: () -> Unit,
    onWearOutfit: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.outfits_gallery_title)) }
            )
        },
        floatingActionButton = {
            OutfitsFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onCreateOutfit = {
                    fabExpanded = false
                    onCreateOutfit()
                },
                onGetSuggestions = {
                    fabExpanded = false
                    onGetSuggestions()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Dismiss the FAB menu when tapping outside it
        if (fabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { fabExpanded = false }
                    )
            )
        }

        if (outfits.isEmpty()) {
            OutfitsEmptyState(
                onCreateOutfit = onCreateOutfit,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(outfits, key = { it.outfit.id }) { outfitWithItems ->
                    OutfitGridCell(
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

/**
 * Expandable FAB for the outfit gallery.
 *
 * When collapsed: shows a single FAB with a "+" / "x" icon.
 * When expanded: shows two [SmallFloatingActionButton]s stacked above the main FAB —
 * "Add outfit" and "Get suggestions" — using [AnimatedVisibility] for the
 * expand/collapse transition.
 */
@Composable
private fun OutfitsFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreateOutfit: () -> Unit,
    onGetSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FabOption(
                    label = stringResource(R.string.outfits_fab_get_suggestions),
                    onClick = onGetSuggestions
                )
                FabOption(
                    label = stringResource(R.string.outfits_fab_add_outfit),
                    onClick = onCreateOutfit
                )
            }
        }

        FloatingActionButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) {
                    stringResource(R.string.outfits_fab_collapse)
                } else {
                    stringResource(R.string.outfits_fab_expand)
                }
            )
        }
    }
}

/**
 * A single labelled small FAB option shown in the expanded FAB menu.
 */
@Composable
private fun FabOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 2.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = label
            )
        }
    }
}

@Composable
private fun OutfitGridCell(
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = outfitWithItems.outfit.name
                    ?: stringResource(R.string.outfits_gallery_untitled),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.outfits_gallery_item_count,
                        outfitWithItems.items.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onWear,
                    enabled = wearEnabled,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (isWearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(R.string.outfits_wear_today),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
