package com.closet.features.wardrobe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.closet.core.data.model.*
import com.closet.core.ui.theme.ClosetTheme
import com.closet.core.ui.util.IconMapper
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class ActiveSheet {
    None, Colors, Materials, Seasons, Occasions, Patterns
}

/**
 * Screen for viewing the details of a specific clothing item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingDetailScreen(
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClothingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf(ActiveSheet.None) }
    
    val colors by viewModel.colors.collectAsStateWithLifecycle()
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val occasions by viewModel.occasions.collectAsStateWithLifecycle()
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.actionError.collectLatest { userMessage ->
            snackbarHostState.showSnackbar(
                message = "Action error: ${userMessage.resId}" 
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wardrobe_delete_item_title)) },
            text = { Text(stringResource(R.string.wardrobe_delete_item_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(onDeleted = {
                            showDeleteDialog = false
                            onBackClick()
                        })
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.wardrobe_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.wardrobe_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is ClothingDetailUiState.Success -> state.item.item.name
                        else -> ""
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wardrobe_back)
                        )
                    }
                },
                actions = {
                    val state = uiState
                    if (state is ClothingDetailUiState.Success) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (state.item.item.isFavorite == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.wardrobe_favorite),
                                tint = if (state.item.item.isFavorite == 1) Color.Red else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { onEditClick(state.item.item.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.wardrobe_edit)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.wardrobe_delete)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ClothingDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ClothingDetailUiState.Error -> {
                    ErrorContent(
                        userMessage = stringResource(state.userMessage.resId, *state.userMessage.args),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ClothingDetailUiState.Success -> {
                    ClothingDetailContent(
                        item = state.item,
                        palette = palette,
                        onWashStatusToggle = { viewModel.toggleWashStatus() },
                        onEditColors = { activeSheet = ActiveSheet.Colors },
                        onEditMaterials = { activeSheet = ActiveSheet.Materials },
                        onEditSeasons = { activeSheet = ActiveSheet.Seasons },
                        onEditOccasions = { activeSheet = ActiveSheet.Occasions },
                        onEditPatterns = { activeSheet = ActiveSheet.Patterns },
                        getAbsoluteFile = { viewModel.getAbsoluteFile(it) }
                    )

                    // Sheet logic
                    when (activeSheet) {
                        ActiveSheet.Colors -> {
                            MultiSelectSheet(
                                title = stringResource(R.string.wardrobe_colors),
                                items = colors.map { MultiSelectItem(it.id, it.name, it, colorHex = it.hex) },
                                selectedIds = state.item.colors.map { it.id }.toSet(),
                                onDismiss = { activeSheet = ActiveSheet.None },
                                onConfirm = { 
                                    viewModel.updateColors(it)
                                    activeSheet = ActiveSheet.None 
                                }
                            )
                        }
                        ActiveSheet.Materials -> {
                            MultiSelectSheet(
                                title = stringResource(R.string.wardrobe_materials),
                                items = materials.map { MultiSelectItem(it.id, it.name, it) },
                                selectedIds = state.item.materials.map { it.id }.toSet(),
                                onDismiss = { activeSheet = ActiveSheet.None },
                                onConfirm = { 
                                    viewModel.updateMaterials(it)
                                    activeSheet = ActiveSheet.None 
                                }
                            )
                        }
                        ActiveSheet.Seasons -> {
                            MultiSelectSheet(
                                title = stringResource(R.string.wardrobe_seasons),
                                items = seasons.map { 
                                    MultiSelectItem(
                                        id = it.id, 
                                        label = it.name, 
                                        original = it, 
                                        iconResId = IconMapper.getIconResource(it.icon)
                                    ) 
                                },
                                selectedIds = state.item.seasons.map { it.id }.toSet(),
                                onDismiss = { activeSheet = ActiveSheet.None },
                                onConfirm = { 
                                    viewModel.updateSeasons(it)
                                    activeSheet = ActiveSheet.None 
                                }
                            )
                        }
                        ActiveSheet.Occasions -> {
                            MultiSelectSheet(
                                title = stringResource(R.string.wardrobe_occasions),
                                items = occasions.map { 
                                    MultiSelectItem(
                                        id = it.id, 
                                        label = it.name, 
                                        original = it, 
                                        iconResId = IconMapper.getIconResource(it.icon)
                                    ) 
                                },
                                selectedIds = state.item.occasions.map { it.id }.toSet(),
                                onDismiss = { activeSheet = ActiveSheet.None },
                                onConfirm = { 
                                    viewModel.updateOccasions(it)
                                    activeSheet = ActiveSheet.None 
                                }
                            )
                        }
                        ActiveSheet.Patterns -> {
                            MultiSelectSheet(
                                title = stringResource(R.string.wardrobe_patterns),
                                items = patterns.map { 
                                    MultiSelectItem(
                                        id = it.id, 
                                        label = it.name, 
                                        original = it, 
                                        iconResId = IconMapper.getPatternIcon(it.name)
                                    ) 
                                },
                                selectedIds = state.item.patterns.map { it.id }.toSet(),
                                onDismiss = { activeSheet = ActiveSheet.None },
                                onConfirm = { 
                                    viewModel.updatePatterns(it)
                                    activeSheet = ActiveSheet.None 
                                }
                            )
                        }
                        ActiveSheet.None -> {}
                    }
                }
            }
        }
    }
}

/**
 * Displays an error message when the item detail fails to load.
 */
@Composable
private fun ErrorContent(
    userMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = userMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Displays the successful content of the clothing item details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClothingDetailContent(
    item: ClothingItemDetail,
    palette: Palette?,
    onWashStatusToggle: () -> Unit,
    onEditColors: () -> Unit,
    onEditMaterials: () -> Unit,
    onEditSeasons: () -> Unit,
    onEditOccasions: () -> Unit,
    onEditPatterns: () -> Unit,
    getAbsoluteFile: (String) -> File,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Image Section
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = palette?.mutedSwatch?.rgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            val imageModel = item.item.imagePath?.let { getAbsoluteFile(it) }
            
            AsyncImage(
                model = imageModel,
                contentDescription = item.item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Header Info Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            item.item.brand?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Group Card
        StatsGroup(
            wearCount = item.wearCount,
            costPerWear = item.costPerWear,
            accentColor = palette?.vibrantSwatch?.rgb?.let { Color(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance Group (Chips)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Chip (Non-interactive)
            Surface(
                shape = SuggestionChipDefaults.shape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = item.item.status.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AssistChip(
                onClick = onWashStatusToggle,
                label = { Text(item.item.washStatus.label) },
                leadingIcon = {
                    Icon(
                        imageVector = if (item.item.washStatus == WashStatus.Clean) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Details Group Card (Main Metadata)
        DetailGroup {
            DetailRow(
                label = stringResource(R.string.wardrobe_category),
                value = item.category?.name ?: stringResource(R.string.wardrobe_uncategorized),
                iconResId = IconMapper.getIconResource(item.category?.icon)
            )
            val subcategory = item.subcategory
            if (subcategory != null) {
                DetailDivider()
                DetailRow(
                    label = stringResource(R.string.wardrobe_subcategory),
                    value = subcategory.name
                )
            }
            val sizeValue = item.sizeValue
            if (sizeValue != null) {
                DetailDivider()
                DetailRow(
                    label = stringResource(R.string.wardrobe_size),
                    value = sizeValue.value
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Purchase Info Group
        val price = item.item.purchasePrice
        val dateStr = item.item.purchaseDate
        val location = item.item.purchaseLocation
        if (price != null || dateStr != null || location != null) {
            DetailGroup(title = stringResource(R.string.wardrobe_purchase_info)) {
                if (price != null) {
                    DetailRow(
                        label = stringResource(R.string.wardrobe_purchase_price),
                        value = currencyFormatter.format(price)
                    )
                }
                if (dateStr != null) {
                    if (price != null) DetailDivider()
                    val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
                    DetailRow(
                        label = stringResource(R.string.wardrobe_purchase_date),
                        value = date?.format(dateFormatter) ?: dateStr
                    )
                }
                if (location != null) {
                    if (price != null || dateStr != null) DetailDivider()
                    DetailRow(
                        label = stringResource(R.string.wardrobe_purchase_location),
                        value = location
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Notes Group
        val notes = item.item.notes
        if (!notes.isNullOrBlank()) {
            DetailGroup(title = stringResource(R.string.wardrobe_notes)) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Attributes Section
        AttributeSection(
            title = stringResource(R.string.wardrobe_colors),
            onEditClick = onEditColors
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.colors.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wardrobe_none_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.colors.forEach { color ->
                        ColorChip(color = color, onClick = onEditColors)
                    }
                }
            }
        }

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
                        SuggestionChip(
                            onClick = onEditMaterials,
                            label = { Text(material.name) }
                        )
                    }
                }
            }
        }

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
                            iconResId = IconMapper.getPatternIcon(pattern.name),
                            onClick = onEditPatterns
                        )
                    }
                }
            }
        }

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
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.wardrobe_edit_with_section, title),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        content()
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ColorChip(
    color: ColorEntity,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(color.name) },
        leadingIcon = {
            val hex = color.hex
            val chipColor = try {
                if (!hex.isNullOrBlank()) Color(hex.toColorInt()) else Color.Gray
            } catch (_: Exception) {
                Color.Gray
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(chipColor, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    )
}

@Composable
private fun AttributeChip(
    label: String,
    iconResId: Int?,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = iconResId?.let {
            {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        }
    )
}

/**
 * Card displaying usage statistics like wear count and cost per wear.
 */
@Composable
private fun StatsGroup(
    wearCount: Int,
    costPerWear: Double?,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentColor?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wardrobe_usage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.wardrobe_worn_times,
                        wearCount,
                        wearCount
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            VerticalDivider(
                modifier = Modifier.height(40.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wardrobe_cost_per_wear),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = if (costPerWear != null) currencyFormatter.format(costPerWear) else "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Generic group card for displaying metadata.
 */
@Composable
private fun DetailGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

/**
 * A single row within a detail group.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    iconResId: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconResId != null) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun ClothingDetailContentPreview() {
    ClosetTheme {
        Surface {
            // Updated preview would need mocked ClothingItemDetail
        }
    }
}
