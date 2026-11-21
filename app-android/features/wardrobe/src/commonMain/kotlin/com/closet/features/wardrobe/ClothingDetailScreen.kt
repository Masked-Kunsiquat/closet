package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.wardrobe_back
import com.closet.features.wardrobe.resources.wardrobe_edit
import com.closet.features.wardrobe.resources.wardrobe_favorite
import com.closet.core.ui.resources.ic_symbol_arrow_back
import com.closet.core.ui.resources.ic_symbol_attach_money
import com.closet.core.ui.resources.ic_symbol_check_circle_filled
import com.closet.core.ui.resources.ic_symbol_delete
import com.closet.core.ui.resources.ic_symbol_edit
import com.closet.core.ui.resources.ic_symbol_favorite
import com.closet.core.ui.resources.ic_symbol_favorite_filled
import com.closet.core.ui.resources.ic_symbol_image
import com.closet.core.ui.resources.ic_symbol_keyboard_arrow_right
import com.closet.core.ui.resources.ic_symbol_numbers
import com.closet.core.ui.resources.ic_symbol_savings
import com.closet.core.ui.resources.ic_symbol_star_filled
import com.closet.features.wardrobe.resources.wardrobe_cancel
import com.closet.features.wardrobe.resources.wardrobe_change_status
import com.closet.features.wardrobe.resources.wardrobe_colors
import com.closet.features.wardrobe.resources.wardrobe_delete
import com.closet.features.wardrobe.resources.wardrobe_delete_item_confirmation
import com.closet.features.wardrobe.resources.wardrobe_delete_item_title
import com.closet.features.wardrobe.resources.wardrobe_detail_from
import com.closet.features.wardrobe.resources.wardrobe_detail_purchased
import com.closet.features.wardrobe.resources.wardrobe_error_load_failed
import com.closet.features.wardrobe.resources.wardrobe_log_wear
import com.closet.features.wardrobe.resources.wardrobe_log_wear_success
import com.closet.features.wardrobe.resources.wardrobe_materials
import com.closet.features.wardrobe.resources.wardrobe_notes
import com.closet.features.wardrobe.resources.wardrobe_occasions
import com.closet.features.wardrobe.resources.wardrobe_patterns
import com.closet.features.wardrobe.resources.wardrobe_seasons
import com.closet.features.wardrobe.resources.wardrobe_stat_cpw
import com.closet.features.wardrobe.resources.wardrobe_stat_cpw_na
import com.closet.features.wardrobe.resources.wardrobe_stat_price
import com.closet.features.wardrobe.resources.wardrobe_stat_wash
import com.closet.features.wardrobe.resources.wardrobe_stat_wears
import com.closet.features.wardrobe.resources.wardrobe_wear_history
import com.closet.features.wardrobe.resources.wardrobe_wear_history_empty
import com.closet.features.wardrobe.resources.wardrobe_wear_history_ootd
import com.closet.features.wardrobe.resources.wardrobe_wear_history_untitled
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.koin.compose.viewmodel.koinViewModel
import com.closet.core.ui.format.formatCurrency
import com.closet.core.ui.format.formatDateLong
import com.closet.core.ui.format.formatMonthYear
import kotlinx.datetime.YearMonth
import kotlinx.datetime.LocalDate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.closet.core.data.dao.ItemWearLog
import com.closet.core.data.model.*
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_icon_washing_machine
import com.closet.core.ui.components.UserMessageSnackbarEffect
import com.closet.core.ui.util.IconMapper

private enum class AttributePicker { SEASONS, OCCASIONS, COLORS, MATERIALS, PATTERNS }

/**
 * Screen displaying full details for a single clothing item.
 *
 * Provides actions for editing, deleting, toggling favorites, and updating wash status.
 * Attributes (colors, seasons, etc.) can be updated directly via bottom sheets.
 *
 * @param onBack Callback to return to the previous screen.
 * @param onEdit Callback to navigate to the edit form for this item.
 * @param onNavigateToJournal Callback to navigate to a specific date in the Outfit Journal.
 * @param viewModel Koin-provided [ClothingDetailViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToJournal: (date: String) -> Unit = {},
    viewModel: ClothingDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val redetectState by viewModel.redetectState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageSnackbarEffect(viewModel.actionError, snackbarHostState)

    val logWearSuccessMessage = stringResource(Res.string.wardrobe_log_wear_success)
    LaunchedEffect(viewModel.logWearSuccess) {
        viewModel.logWearSuccess.collect {
            snackbarHostState.showSnackbar(logWearSuccessMessage)
        }
    }

    var activePicker by remember { mutableStateOf<AttributePicker?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        val currentStatus = (uiState as? ClothingDetailUiState.Success)?.item?.item?.status
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(stringResource(Res.string.wardrobe_change_status)) },
            text = {
                Column {
                    ClothingStatus.entries.forEach { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateStatus(status)
                                    showStatusDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = status == currentStatus,
                                onClick = {
                                    viewModel.updateStatus(status)
                                    showStatusDialog = false
                                },
                            )
                            Text(status.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text(stringResource(Res.string.wardrobe_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.wardrobe_delete_item_title)) },
            text = { Text(stringResource(Res.string.wardrobe_delete_item_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteItem(onBack)
                }) {
                    Text(stringResource(Res.string.wardrobe_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.wardrobe_cancel))
                }
            },
        )
    }

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
                        Icon(painterResource(CoreRes.drawable.ic_symbol_arrow_back), contentDescription = stringResource(Res.string.wardrobe_back))
                    }
                },
                actions = {
                    (uiState as? ClothingDetailUiState.Success)?.item?.let { detail ->
                        IconButton(onClick = { onEdit(detail.item.id) }) {
                            Icon(painterResource(CoreRes.drawable.ic_symbol_edit), contentDescription = stringResource(Res.string.wardrobe_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(painterResource(CoreRes.drawable.ic_symbol_delete), contentDescription = stringResource(Res.string.wardrobe_delete))
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
                    Text(stringResource(Res.string.wardrobe_error_load_failed))
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
                                painter = painterResource(CoreRes.drawable.ic_symbol_image),
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
                                painter = if (detail.item.isFavorite == 1) painterResource(CoreRes.drawable.ic_symbol_favorite_filled) else painterResource(CoreRes.drawable.ic_symbol_favorite),
                                contentDescription = stringResource(Res.string.wardrobe_favorite),
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

                            // Status Badge — tappable
                            FilterChip(
                                selected = true,
                                onClick = { showStatusDialog = true },
                                label = { Text(detail.item.status.label, style = MaterialTheme.typography.labelLarge) },
                            )
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
                        DetailStatRow(
                            washStatus = detail.item.washStatus,
                            wearCount = detail.wearCount,
                            purchasePrice = detail.item.purchasePrice,
                            onToggleWash = { viewModel.toggleWashStatus() },
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { viewModel.logWearToday() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(CoreRes.drawable.ic_symbol_check_circle_filled),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.wardrobe_log_wear))
                        }

                        // Purchase metadata — only rendered when at least one field is set
                        if (detail.item.purchaseDate != null || !detail.item.purchaseLocation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            PurchaseMetaRow(
                                purchaseDate = detail.item.purchaseDate,
                                purchaseLocation = detail.item.purchaseLocation,
                            )
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
                            onEditSize = { onEdit(detail.item.id) },
                            onRedetectColors = if (detail.item.imagePath != null) {
                                { viewModel.onRedetectColors() }
                            } else {
                                null
                            },
                            isRedetecting = redetectState == RedetectState.Measuring,
                        )

                        // Notes
                        detail.item.notes?.let { notes ->
                            if (notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(Res.string.wardrobe_notes),
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

                        if (viewModel.showDebugCard) {
                            SemanticDebugCard(
                                semanticDescription = detail.item.semanticDescription,
                                imageCaption = detail.item.imageCaption,
                            )
                        }
                    }
                }
            }
        }
    }

    // Re-detect colors preview sheet — driven by the ViewModel's redetectState
    (redetectState as? RedetectState.Preview)?.let { preview ->
        RedetectColorsSheet(
            current = preview.current,
            proposed = preview.proposed,
            onApply = viewModel::onApplyRedetect,
            onCancel = viewModel::onCancelRedetect,
        )
    }

    // Attribute picker sheet — driven by activePicker state
    activePicker?.let { picker ->
        val successState = uiState as? ClothingDetailUiState.Success
        val successItem = successState?.item
        val title = stringResource(when (picker) {
            AttributePicker.SEASONS   -> Res.string.wardrobe_seasons
            AttributePicker.OCCASIONS -> Res.string.wardrobe_occasions
            AttributePicker.COLORS    -> Res.string.wardrobe_colors
            AttributePicker.MATERIALS -> Res.string.wardrobe_materials
            AttributePicker.PATTERNS  -> Res.string.wardrobe_patterns
        })
        val items = when (picker) {
            AttributePicker.SEASONS   -> successState?.seasons.orEmpty().map { MultiSelectItem(it.id, it.name, it, icon = IconMapper.getIconResource(it.icon)) }
            AttributePicker.OCCASIONS -> successState?.occasions.orEmpty().map { MultiSelectItem(it.id, it.name, it, icon = IconMapper.getIconResource(it.icon)) }
            AttributePicker.COLORS    -> successState?.colors.orEmpty().map { MultiSelectItem(it.id, it.name, it, colorHex = it.hex) }
            AttributePicker.MATERIALS -> successState?.materials.orEmpty().map { MultiSelectItem(it.id, it.name, it) }
            AttributePicker.PATTERNS  -> successState?.patterns.orEmpty().map { MultiSelectItem(it.id, it.name, it, icon = IconMapper.getIconResource(it.icon)) }
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

// ─── Stat cards ──────────────────────────────────────────────────────────────

/**
 * 2×2 grid of horizontal pill cards: Wash (tappable) + Wears on top,
 * Price + Cost-per-wear on the bottom.
 *
 * Price and CPW show "—" when [purchasePrice] is null or [wearCount] is 0.
 */
@Composable
internal fun DetailStatRow(
    washStatus: WashStatus,
    wearCount: Int,
    purchasePrice: Double?,
    onToggleWash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val naText = stringResource(Res.string.wardrobe_stat_cpw_na)
    val priceText = if (purchasePrice != null) formatCurrency(purchasePrice) else naText
    val cpwText = if (purchasePrice != null && wearCount > 0)
        formatCurrency(purchasePrice / wearCount)
    else
        naText

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPillCard(
                icon = {
                    Icon(
                        painter = painterResource(CoreRes.drawable.ic_icon_washing_machine),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = stringResource(Res.string.wardrobe_stat_wash),
                value = washStatus.label,
                valueColor = if (washStatus == WashStatus.Dirty)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                onClick = onToggleWash,
                modifier = Modifier.weight(1f).height(56.dp),
            )
            StatPillCard(
                icon = {
                    Icon(
                        painter = painterResource(CoreRes.drawable.ic_symbol_numbers),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = stringResource(Res.string.wardrobe_stat_wears),
                value = wearCount.toString(),
                modifier = Modifier.weight(1f).height(56.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPillCard(
                icon = {
                    Icon(
                        painter = painterResource(CoreRes.drawable.ic_symbol_attach_money),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = stringResource(Res.string.wardrobe_stat_price),
                value = priceText,
                modifier = Modifier.weight(1f).height(56.dp),
            )
            StatPillCard(
                icon = {
                    Icon(
                        painter = painterResource(CoreRes.drawable.ic_symbol_savings),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = stringResource(Res.string.wardrobe_stat_cpw),
                value = cpwText,
                modifier = Modifier.weight(1f).height(56.dp),
            )
        }
    }
}

/**
 * Single horizontal pill stat card: [icon] label on the left, bold value on the right.
 * Pass [onClick] to make the card tappable (e.g. the wash-status toggle).
 */
@Composable
private fun StatPillCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier) { content() }
    } else {
        OutlinedCard(modifier = modifier) { content() }
    }
}

/**
 * Displays the purchase date (formatted as "Month YYYY") and/or purchase location
 * as a simple two-row label/value section. Each row is only rendered if the value is non-null.
 */
@Composable
private fun PurchaseMetaRow(
    purchaseDate: String?,
    purchaseLocation: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        purchaseDate?.let { raw ->
            val formatted = runCatching { formatMonthYear(LocalDate.parse(raw).let { YearMonth(it.year, it.month) }) }
                .getOrDefault(raw)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.wardrobe_detail_purchased),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = formatted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        purchaseLocation?.let { location ->
            if (location.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.wardrobe_detail_from),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = location, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Displays a vertical list of dates and outfit names where this item was logged as worn.
 * Shows an empty state message if the item has never been logged.
 *
 * @param history List of [ItemWearLog] entries sorted by date (usually descending).
 * @param onEntryClick Callback triggered when a row is tapped, passing the ISO date string.
 */
@Composable
private fun WearHistorySection(
    history: List<ItemWearLog>,
    onEntryClick: (date: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.wardrobe_wear_history),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(Res.string.wardrobe_wear_history_empty),
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

/**
 * A single row in the wear history list.
 *
 * Displays the formatted date, the outfit name (or "Untitled"), and an optional
 * "Outfit of the Day" star icon.
 *
 * @param log The wear log data for this row.
 * @param onClick Callback when the row is tapped.
 */
@Composable
private fun WearHistoryRow(
    log: ItemWearLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = remember(log.date) {
        runCatching { formatDateLong(LocalDate.parse(log.date)) }
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
                text = if (log.outfitName.isNullOrBlank()) stringResource(Res.string.wardrobe_wear_history_untitled) else log.outfitName!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (log.isOotd == 1) {
            Icon(
                painter = painterResource(CoreRes.drawable.ic_symbol_star_filled),
                contentDescription = stringResource(Res.string.wardrobe_wear_history_ootd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            painter = painterResource(CoreRes.drawable.ic_symbol_keyboard_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
