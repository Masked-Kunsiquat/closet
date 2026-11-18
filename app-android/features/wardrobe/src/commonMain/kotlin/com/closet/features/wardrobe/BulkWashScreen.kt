package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_arrow_back
import com.closet.features.wardrobe.resources.bulk_wash_apply_error
import com.closet.features.wardrobe.resources.bulk_wash_deselect_all
import com.closet.features.wardrobe.resources.bulk_wash_mark_clean
import com.closet.features.wardrobe.resources.bulk_wash_mark_dirty
import com.closet.features.wardrobe.resources.bulk_wash_select_all
import com.closet.features.wardrobe.resources.bulk_wash_selected_count
import com.closet.features.wardrobe.resources.bulk_wash_title
import com.closet.features.wardrobe.resources.wardrobe_back
import com.closet.features.wardrobe.resources.wardrobe_empty_closet
import com.closet.features.wardrobe.resources.wardrobe_error_load_failed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.closet.core.data.model.WashStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BulkWashScreen(
    onBack: () -> Unit,
    viewModel: BulkWashViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BulkWashContent(
        uiState = uiState,
        onBack = onBack,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = { ids -> viewModel.selectAll(ids) },
        onClearSelection = viewModel::clearSelection,
        onMarkClean = { viewModel.applyWashStatus(WashStatus.Clean) },
        onMarkDirty = { viewModel.applyWashStatus(WashStatus.Dirty) },
        onDismissApplyError = viewModel::dismissApplyError,
        resolveImage = viewModel::resolveImagePath,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkWashContent(
    uiState: BulkWashUiState,
    onBack: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onMarkClean: () -> Unit,
    onMarkDirty: () -> Unit,
    onDismissApplyError: () -> Unit,
    resolveImage: (String?) -> String?,
) {
    val successState = uiState as? BulkWashUiState.Success
    val selectedCount = successState?.selectedIds?.size ?: 0
    val allIds = successState?.items?.map { it.id } ?: emptyList()
    val allSelected = allIds.isNotEmpty() && successState?.selectedIds?.containsAll(allIds) == true

    val snackbarHostState = remember { SnackbarHostState() }
    val applyErrorMessage = stringResource(Res.string.bulk_wash_apply_error)
    LaunchedEffect(successState?.applyError) {
        if (successState?.applyError == true) {
            snackbarHostState.showSnackbar(applyErrorMessage)
            onDismissApplyError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.bulk_wash_title))
                        if (selectedCount > 0) {
                            Text(
                                text = pluralStringResource(Res.plurals.bulk_wash_selected_count, selectedCount, selectedCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.ic_symbol_arrow_back),
                            contentDescription = stringResource(Res.string.wardrobe_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (allSelected) onClearSelection() else onSelectAll(allIds)
                        }
                    ) {
                        Text(
                            if (allSelected) stringResource(Res.string.bulk_wash_deselect_all)
                            else stringResource(Res.string.bulk_wash_select_all)
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onMarkClean,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.bulk_wash_mark_clean))
                        }
                        Button(
                            onClick = onMarkDirty,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.bulk_wash_mark_dirty))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (uiState) {
            is BulkWashUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is BulkWashUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.wardrobe_error_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is BulkWashUiState.Success -> {
                if (uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.wardrobe_empty_closet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            BulkWashItemRow(
                                item = item,
                                isSelected = item.id in uiState.selectedIds,
                                onToggle = { onToggleSelection(item.id) },
                                resolveImage = resolveImage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkWashItemRow(
    item: com.closet.core.data.model.ClothingItemWithMeta,
    isSelected: Boolean,
    onToggle: () -> Unit,
    resolveImage: (String?) -> String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = resolveImage(item.imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(item.categoryName, item.subcategoryName)
                .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.washStatus.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.washStatus == WashStatus.Dirty)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
    }
}
