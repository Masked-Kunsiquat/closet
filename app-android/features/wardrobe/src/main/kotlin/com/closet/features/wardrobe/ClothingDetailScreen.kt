package com.closet.features.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.dao.ItemWearLog
import com.closet.core.data.model.*
import com.closet.core.ui.R as CoreR
import com.closet.core.ui.components.UserMessageSnackbarEffect
import com.closet.core.ui.util.IconMapper

private enum class AttributePicker { SEASONS, OCCASIONS, COLORS, MATERIALS, PATTERNS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToJournal: (date: String) -> Unit = {},
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageSnackbarEffect(viewModel.actionError, snackbarHostState)

    var activePicker by remember { mutableStateOf<AttributePicker?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val name = (uiState as? ClothingDetailUiState.Success)?.item?.item?.name ?: ""
                    Text(name)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    (uiState as? ClothingDetailUiState.Success)?.item?.let { detail ->
                        IconButton(onClick = { onEdit(detail.item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deleteItem(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ClothingDetailUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ClothingDetailUiState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding), contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.wardrobe_error_load_failed))
                }
            }
            is ClothingDetailUiState.Success -> {
                val detail = state.item
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                ) {
                    // Main Image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        detail.item.imagePath?.let { path ->
                            AsyncImage(
                                model = viewModel.resolveImagePath(path),
                                contentDescription = detail.item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: run {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        // Favorite Toggle
                        FilledTonalIconButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = if (detail.item.isFavorite == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (detail.item.isFavorite == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = detail.item.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                detail.brand?.name?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Status Badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = detail.item.status.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category/Subcategory
                        Text(
                            text = "${detail.category?.name ?: ""} • ${detail.subcategory?.name ?: ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Quick Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedCard(
                                onClick = { viewModel.toggleWashStatus() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(id = CoreR.drawable.ic_icon_washing_machine),
                                        contentDescription = null,
                                        tint = if (detail.item.washStatus == WashStatus.Dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = detail.item.washStatus.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            OutlinedCard(
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Numbers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.wardrobe_worn_times,
                                            detail.wearCount,
                                            detail.wearCount
                                        ),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            detail.item.purchasePrice?.let { price ->
                                OutlinedCard(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachMoney,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$${String.format(Locale.getDefault(), "%.2f", price)}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Attributes
                        ClothingAttributes(
                            item = detail,
                            sizeSystems = state.sizeSystems,
                            onEditSeasons = { activePicker = AttributePicker.SEASONS },
                            onEditOccasions = { activePicker = AttributePicker.OCCASIONS },
                            onEditColors = { activePicker = AttributePicker.COLORS },
                            onEditMaterials = { activePicker = AttributePicker.MATERIALS },
                            onEditPatterns = { activePicker = AttributePicker.PATTERNS },
                            onEditSize = { onEdit(detail.item.id) }
                        )

                        // Notes
                        detail.item.notes?.let { notes ->
                            if (notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.wardrobe_notes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        // Wear history
                        Spacer(modifier = Modifier.height(24.dp))
                        WearHistorySection(
                            history = state.wearHistory,
                            onEntryClick = onNavigateToJournal,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Attribute picker sheet — driven by activePicker state
    activePicker?.let { picker ->
        val successState = uiState as? ClothingDetailUiState.Success
        val successItem = successState?.item
        val title = stringResource(when (picker) {
            AttributePicker.SEASONS   -> R.string.wardrobe_seasons
            AttributePicker.OCCASIONS -> R.string.wardrobe_occasions
            AttributePicker.COLORS    -> R.string.wardrobe_colors
            AttributePicker.MATERIALS -> R.string.wardrobe_materials
            AttributePicker.PATTERNS  -> R.string.wardrobe_patterns
        })
        val items = when (picker) {
            AttributePicker.SEASONS   -> successState?.seasons.orEmpty().map { MultiSelectItem(it.id, it.name, it, iconResId = IconMapper.getIconResource(it.icon)) }
            AttributePicker.OCCASIONS -> successState?.occasions.orEmpty().map { MultiSelectItem(it.id, it.name, it, iconResId = IconMapper.getIconResource(it.icon)) }
            AttributePicker.COLORS    -> successState?.colors.orEmpty().map { MultiSelectItem(it.id, it.name, it, colorHex = it.hex) }
            AttributePicker.MATERIALS -> successState?.materials.orEmpty().map { MultiSelectItem(it.id, it.name, it) }
            AttributePicker.PATTERNS  -> successState?.patterns.orEmpty().map { MultiSelectItem(it.id, it.name, it, iconResId = IconMapper.getIconResource(it.icon)) }
        }
        val selectedIds = when (picker) {
            AttributePicker.SEASONS   -> successItem?.seasons?.map { it.id }?.toSet()
            AttributePicker.OCCASIONS -> successItem?.occasions?.map { it.id }?.toSet()
            AttributePicker.COLORS    -> successItem?.colors?.map { it.id }?.toSet()
            AttributePicker.MATERIALS -> successItem?.materials?.map { it.id }?.toSet()
            AttributePicker.PATTERNS  -> successItem?.patterns?.map { it.id }?.toSet()
        } ?: emptySet()

        MultiSelectSheet(
            title = title,
            items = items,
            selectedIds = selectedIds,
            onDismiss = { activePicker = null },
            onConfirm = { selected ->
                when (picker) {
                    AttributePicker.SEASONS   -> viewModel.updateSeasons(selected)
                    AttributePicker.OCCASIONS -> viewModel.updateOccasions(selected)
                    AttributePicker.COLORS    -> viewModel.updateColors(selected)
                    AttributePicker.MATERIALS -> viewModel.updateMaterials(selected)
                    AttributePicker.PATTERNS  -> viewModel.updatePatterns(selected)
                }
                activePicker = null
            }
        )
    }
}

// ─── Wear history section ─────────────────────────────────────────────────────

@Composable
private fun WearHistorySection(
    history: List<ItemWearLog>,
    onEntryClick: (date: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wardrobe_wear_history),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.wardrobe_wear_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            history.forEachIndexed { index, log ->
                WearHistoryRow(
                    log = log,
                    onClick = { onEntryClick(log.date) },
                )
                if (index < history.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WearHistoryRow(
    log: ItemWearLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = remember(log.date) {
        runCatching { LocalDate.parse(log.date).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) }
            .getOrElse { log.date }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (log.outfitName.isNullOrBlank()) stringResource(R.string.wardrobe_wear_history_untitled) else log.outfitName!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (log.isOotd == 1) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.wardrobe_wear_history_ootd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
