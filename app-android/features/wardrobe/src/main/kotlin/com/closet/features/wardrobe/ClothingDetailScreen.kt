package com.closet.features.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.*
import com.closet.core.ui.R
import com.closet.core.ui.util.IconMapper
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api?, ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showSeasonPicker by remember { mutableStateOf(false) }
    var showOccasionPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showMaterialPicker by remember { mutableStateOf(false) }
    var showPatternPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.clothingItem?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.item?.let { detail ->
                        IconButton(onClick = { onEdit(detail.clothingItem.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deleteItem() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        uiState.item?.let { detail ->
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
                    detail.clothingItem.imagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = detail.clothingItem.name,
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
                            imageVector = if (detail.clothingItem.isFavorite == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (detail.clothingItem.isFavorite == 1) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = detail.clothingItem.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            detail.clothingItem.brand?.let {
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
                                text = detail.clothingItem.status,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Category/Subcategory
                    Text(
                        text = "${detail.categoryName} • ${detail.subcategoryName}",
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
                                    painter = painterResource(id = R.drawable.ic_icon_washing_machine),
                                    contentDescription = null,
                                    tint = if (detail.clothingItem.washStatus == "Dirty") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = detail.clothingItem.washStatus,
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
                                    text = "${detail.wearCount} ${stringResource(R.string.wardrobe_wears)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        
                        detail.clothingItem.purchasePrice?.let { price ->
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
                                        text = "$${String.format("%.2f", price)}",
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
                        onEditSeasons = { showSeasonPicker = true },
                        onEditOccasions = { showOccasionPicker = true },
                        onEditColors = { showColorPicker = true },
                        onEditMaterials = { showMaterialPicker = true },
                        onEditPatterns = { showPatternPicker = true }
                    )

                    // Notes
                    detail.clothingItem.notes?.let { notes ->
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
                }
            }
        }
    }

    // Multi-Select Sheets
    if (showSeasonPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_seasons),
            items = uiState.allSeasons.map { SelectableItem(it.id, it.name, it.icon) },
            selectedIds = uiState.item?.seasons?.map { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showSeasonPicker = false },
            onConfirm = { viewModel.updateSeasons(it) }
        )
    }

    if (showOccasionPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_occasions),
            items = uiState.allOccasions.map { SelectableItem(it.id, it.name, it.icon) },
            selectedIds = uiState.item?.occasions?.map { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showOccasionPicker = false },
            onConfirm = { viewModel.updateOccasions(it) }
        )
    }

    if (showColorPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_colors),
            items = uiState.allColors.map { SelectableItem(it.id, it.name, hex = it.hex) },
            selectedIds = uiState.item?.colors?.map { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showColorPicker = false },
            onConfirm = { viewModel.updateColors(it) }
        )
    }

    if (showMaterialPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_materials),
            items = uiState.allMaterials.map { SelectableItem(it.id, it.name) },
            selectedIds = uiState.item?.materials?.map { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showMaterialPicker = false },
            onConfirm = { viewModel.updateMaterials(it) }
        )
    }

    if (showPatternPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_patterns),
            items = uiState.allPatterns.map { SelectableItem(it.id, it.name) },
            selectedIds = uiState.item?.patterns?.map { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showPatternPicker = false },
            onConfirm = { viewModel.updatePatterns(it) }
        )
    }

    // Handle Delete Success
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onBack()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClothingAttributes(
    item: ClothingItemDetail,
    onEditSeasons: () -> Unit,
    onEditOccasions: () -> Unit,
    onEditColors: () -> Unit,
    onEditMaterials: () -> Unit,
    onEditPatterns: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Seasons
        AttributeSection(
            title = stringResource(R.string.wardrobe_seasons),
            onEditClick = onEditSeasons
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.seasons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.seasons.forEach { season ->
                        AttributeChip(
                            label = season.name,
                            iconResId = IconMapper.getIconResource(season.icon),
                            onClick = onEditSeasons
                        )
                    }
                }
            }
        }

        // Colors
        AttributeSection(
            title = stringResource(R.string.wardrobe_colors),
            onEditClick = onEditColors
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.colors.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.colors.forEach { color ->
                        AttributeChip(
                            label = color.name,
                            color = color.hex?.let { Color(android.graphics.Color.parseColor(it)) },
                            onClick = onEditColors
                        )
                    }
                }
            }
        }

        // Materials
        AttributeSection(
            title = stringResource(R.string.wardrobe_materials),
            onEditClick = onEditMaterials
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.materials.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.materials.forEach { material ->
                        AttributeChip(
                            label = material.name,
                            onClick = onEditMaterials
                        )
                    }
                }
            }
        }

        // Patterns
        AttributeSection(
            title = stringResource(R.string.wardrobe_patterns),
            onEditClick = onEditPatterns
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.patterns.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.patterns.forEach { pattern ->
                        AttributeChip(
                            label = pattern.name,
                            onClick = onEditPatterns
                        )
                    }
                }
            }
        }

        AttributeSection(
            title = stringResource(R.string.wardrobe_occasions),
            onEditClick = onEditOccasions
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.occasions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.occasions.forEach { occasion ->
                        AttributeChip(
                            label = occasion.name,
                            iconResId = IconMapper.getIconResource(occasion.icon),
                            onClick = onEditOccasions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeSection(
    title: String,
    onEditClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Edit $title",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun AttributeChip(
    label: String,
    iconResId: Int? = null,
    color: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (color != null) null else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (color != null) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            } else if (iconResId != null) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
