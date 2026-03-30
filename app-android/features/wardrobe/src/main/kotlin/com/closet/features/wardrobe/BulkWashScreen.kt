package com.closet.features.wardrobe

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.WashStatus

@Composable
fun BulkWashScreen(
    onBack: () -> Unit,
    viewModel: BulkWashViewModel = hiltViewModel(),
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
    resolveImage: (String?) -> java.io.File?,
) {
    val successState = uiState as? BulkWashUiState.Success
    val selectedCount = successState?.selectedIds?.size ?: 0
    val allIds = successState?.items?.map { it.id } ?: emptyList()
    val allSelected = allIds.isNotEmpty() && successState?.selectedIds?.containsAll(allIds) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.bulk_wash_title))
                        if (selectedCount > 0) {
                            Text(
                                text = stringResource(R.string.bulk_wash_selected_count, selectedCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wardrobe_back),
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
                            if (allSelected) stringResource(R.string.bulk_wash_deselect_all)
                            else stringResource(R.string.bulk_wash_select_all)
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
                            Text(stringResource(R.string.bulk_wash_mark_clean))
                        }
                        Button(
                            onClick = onMarkDirty,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.bulk_wash_mark_dirty))
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
                        text = stringResource(R.string.wardrobe_error_load_failed),
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
                            text = stringResource(R.string.wardrobe_empty_closet),
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
    resolveImage: (String?) -> java.io.File?,
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
