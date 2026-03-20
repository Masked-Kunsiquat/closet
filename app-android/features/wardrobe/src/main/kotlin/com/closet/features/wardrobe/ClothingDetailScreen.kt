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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.model.*
import com.closet.core.ui.R as CoreR
import com.closet.core.ui.util.IconMapper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allSeasons by viewModel.seasons.collectAsStateWithLifecycle()
    val allOccasions by viewModel.occasions.collectAsStateWithLifecycle()
    val allColors by viewModel.colors.collectAsStateWithLifecycle()
    val allMaterials by viewModel.materials.collectAsStateWithLifecycle()
    val allPatterns by viewModel.patterns.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            val text = if (message.args.isEmpty()) {
                context.getString(message.resId)
            } else {
                context.getString(message.resId, *message.args)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    var showSeasonPicker by remember { mutableStateOf(false) }
    var showOccasionPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showMaterialPicker by remember { mutableStateOf(false) }
    var showPatternPicker by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                                model = viewModel.getAbsoluteFile(path),
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
                                tint = if (detail.item.isFavorite == 1) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
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
                                detail.item.brand?.let {
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
                    }
                }
            }
        }
    }

    // Multi-Select Sheets
    if (showSeasonPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_seasons),
            items = allSeasons.map {
                MultiSelectItem(
                    it.id,
                    it.name,
                    it,
                    iconResId = IconMapper.getIconResource(it.icon)
                )
            },
            selectedIds = (uiState as? ClothingDetailUiState.Success)?.item?.seasons?.map { it.id }
                ?.toSet() ?: emptySet(),
            onDismiss = { showSeasonPicker = false },
            onConfirm = {
                viewModel.updateSeasons(it)
                showSeasonPicker = false
            }
        )
    }

    if (showOccasionPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_occasions),
            items = allOccasions.map {
                MultiSelectItem(
                    it.id,
                    it.name,
                    it,
                    iconResId = IconMapper.getIconResource(it.icon)
                )
            },
            selectedIds = (uiState as? ClothingDetailUiState.Success)?.item?.occasions?.map { it.id }
                ?.toSet() ?: emptySet(),
            onDismiss = { showOccasionPicker = false },
            onConfirm = {
                viewModel.updateOccasions(it)
                showOccasionPicker = false
            }
        )
    }

    if (showColorPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_colors),
            items = allColors.map { MultiSelectItem(it.id, it.name, it, colorHex = it.hex) },
            selectedIds = (uiState as? ClothingDetailUiState.Success)?.item?.colors?.map { it.id }
                ?.toSet() ?: emptySet(),
            onDismiss = { showColorPicker = false },
            onConfirm = {
                viewModel.updateColors(it)
                showColorPicker = false
            }
        )
    }

    if (showMaterialPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_materials),
            items = allMaterials.map { MultiSelectItem(it.id, it.name, it) },
            selectedIds = (uiState as? ClothingDetailUiState.Success)?.item?.materials?.map { it.id }
                ?.toSet() ?: emptySet(),
            onDismiss = { showMaterialPicker = false },
            onConfirm = {
                viewModel.updateMaterials(it)
                showMaterialPicker = false
            }
        )
    }

    if (showPatternPicker) {
        MultiSelectSheet(
            title = stringResource(R.string.wardrobe_patterns),
            items = allPatterns.map { MultiSelectItem(it.id, it.name, it) },
            selectedIds = (uiState as? ClothingDetailUiState.Success)?.item?.patterns?.map { it.id }
                ?.toSet() ?: emptySet(),
            onDismiss = { showPatternPicker = false },
            onConfirm = {
                viewModel.updatePatterns(it)
                showPatternPicker = false
            }
        )
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
                            color = color.hex?.let {
                                try {
                                    Color(android.graphics.Color.parseColor(it))
                                } catch (e: Exception) {
                                    null
                                }
                            },
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

@OptIn(ExperimentalMaterial3Api::class)
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
        color = MaterialTheme.colorScheme.surfaceVariant
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
