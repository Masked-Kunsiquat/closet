package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_add
import com.closet.core.ui.resources.ic_symbol_straighten
import com.closet.features.wardrobe.resources.wardrobe_cancel
import com.closet.features.wardrobe.resources.wardrobe_colors
import com.closet.features.wardrobe.resources.wardrobe_edit_with_section
import com.closet.features.wardrobe.resources.wardrobe_materials
import com.closet.features.wardrobe.resources.wardrobe_none_selected
import com.closet.features.wardrobe.resources.wardrobe_occasions
import com.closet.features.wardrobe.resources.wardrobe_patterns
import com.closet.features.wardrobe.resources.wardrobe_redetect_apply
import com.closet.features.wardrobe.resources.wardrobe_redetect_colors
import com.closet.features.wardrobe.resources.wardrobe_redetect_current
import com.closet.features.wardrobe.resources.wardrobe_redetect_proposed
import com.closet.features.wardrobe.resources.wardrobe_redetect_share
import com.closet.features.wardrobe.resources.wardrobe_seasons
import com.closet.features.wardrobe.resources.wardrobe_size
import com.closet.core.ui.format.parseHexColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.ui.util.IconMapper
import kotlin.math.roundToInt

// ─── ClothingAttributes ───────────────────────────────────────────────────────

/**
 * Displays a categorized list of clothing attributes (Size, Seasons, Colors, etc.).
 *
 * Each section provides an "Add" or "Edit" button that triggers the respective
 * callback. If no attributes are present for a section, a "None selected" hint is shown.
 *
 * @param item The detail view of the clothing item containing its attributes.
 * @param sizeSystems The list of all available size systems, used to resolve the system name for display.
 * @param onEditSeasons Callback to open the season picker.
 * @param onEditOccasions Callback to open the occasion picker.
 * @param onEditColors Callback to open the color picker.
 * @param onEditMaterials Callback to open the material picker.
 * @param onEditPatterns Callback to open the pattern picker.
 * @param onEditSize Callback to navigate to the form for size editing.
 * @param onRedetectColors Re-detects the colours from the photo; null hides the action (no photo).
 * @param isRedetecting True while the photo is being measured: the action shows progress and is disabled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClothingAttributes(
    item: ClothingItemDetail,
    sizeSystems: List<SizeSystemEntity>,
    onEditSeasons: () -> Unit,
    onEditOccasions: () -> Unit,
    onEditColors: () -> Unit,
    onEditMaterials: () -> Unit,
    onEditPatterns: () -> Unit,
    onEditSize: () -> Unit,
    onRedetectColors: (() -> Unit)?,
    isRedetecting: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Size Attribute
        if (item.sizeValue != null) {
            AttributeSection(
                title = stringResource(Res.string.wardrobe_size),
                onEditClick = onEditSize
            ) {
                val systemName = sizeSystems.find { it.id == item.sizeValue?.sizeSystemId }?.name
                val label = when {
                    systemName == "One Size" -> item.sizeValue?.value ?: ""
                    systemName != null -> "${item.sizeValue?.value}  ·  $systemName"
                    else -> item.sizeValue?.value ?: ""
                }
                
                AttributeChip(
                    label = label,
                    iconRes = CoreRes.drawable.ic_symbol_straighten,
                    onClick = onEditSize
                )
            }
        }

        AttributeSection(
            title = stringResource(Res.string.wardrobe_seasons),
            onEditClick = onEditSeasons
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.seasons.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.seasons.forEach { season ->
                        AttributeChip(
                            label = season.name,
                            iconRes = IconMapper.getIconResource(season.icon),
                            onClick = onEditSeasons
                        )
                    }
                }
            }
        }

        AttributeSection(
            title = stringResource(Res.string.wardrobe_colors),
            onEditClick = onEditColors,
            headerAction = onRedetectColors?.let { onRedetect ->
                { RedetectColorsAction(onClick = onRedetect, isRedetecting = isRedetecting) }
            },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.colors.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.colors.forEach { color ->
                        AttributeChip(
                            label = color.name,
                            color = parseHexColor(color.hex),
                            onClick = onEditColors
                        )
                    }
                }
            }
        }

        AttributeSection(
            title = stringResource(Res.string.wardrobe_materials),
            onEditClick = onEditMaterials
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.materials.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.materials.forEach { material ->
                        AttributeChip(label = material.name, onClick = onEditMaterials)
                    }
                }
            }
        }

        AttributeSection(
            title = stringResource(Res.string.wardrobe_patterns),
            onEditClick = onEditPatterns
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.patterns.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.patterns.forEach { pattern ->
                        AttributeChip(
                            label = pattern.name,
                            iconRes = IconMapper.getIconResource(pattern.icon),
                            onClick = onEditPatterns
                        )
                    }
                }
            }
        }

        AttributeSection(
            title = stringResource(Res.string.wardrobe_occasions),
            onEditClick = onEditOccasions
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.occasions.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.occasions.forEach { occasion ->
                        AttributeChip(
                            label = occasion.name,
                            iconRes = IconMapper.getIconResource(occasion.icon),
                            onClick = onEditOccasions
                        )
                    }
                }
            }
        }
    }
}

// ─── AttributeSection ─────────────────────────────────────────────────────────

/**
 * A layout wrapper for an attribute category (e.g., "Seasons").
 *
 * Displays a bold title and an edit icon button above the provided [content].
 *
 * @param title The section heading.
 * @param onEditClick Callback for the edit action.
 * @param headerAction Optional extra action shown just before the edit button.
 * @param content The attribute-specific UI (usually a FlowRow of chips).
 */
@Composable
internal fun AttributeSection(
    title: String,
    onEditClick: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerAction?.invoke()
                IconButton(onClick = onEditClick) {
                    Icon(
                        painter = painterResource(CoreRes.drawable.ic_symbol_add),
                        contentDescription = stringResource(Res.string.wardrobe_edit_with_section, title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

// ─── AttributeChip ────────────────────────────────────────────────────────────

/**
 * A specialized chip used to display a single attribute value.
 *
 * Supports an optional leading icon (resource) or a color swatch.
 *
 * @param label The text to display.
 * @param iconRes Optional drawable resource for the leading icon.
 * @param color Optional [Color] for a leading circular swatch.
 * @param onClick Callback when the chip is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttributeChip(
    label: String,
    iconRes: DrawableResource? = null,
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
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─── AttributeEmptyHint ───────────────────────────────────────────────────────

/**
 * Placeholder text shown when an attribute section has no selections.
 */
@Composable
private fun AttributeEmptyHint() {
    Text(
        text = stringResource(Res.string.wardrobe_none_selected),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ─── Re-detect colors ─────────────────────────────────────────────────────────

/**
 * The Colors-section "Re-detect colors" text button. While [isRedetecting] it is disabled and
 * shows a small progress indicator before its label.
 */
@Composable
private fun RedetectColorsAction(onClick: () -> Unit, isRedetecting: Boolean) {
    TextButton(onClick = onClick, enabled = !isRedetecting) {
        if (isRedetecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(Res.string.wardrobe_redetect_colors))
    }
}

/**
 * Bottom sheet previewing a colour re-detection: the item's [current] colours above the
 * [proposed] ones (measured swatch, name and share). Apply writes the proposal; Cancel, or
 * dismissing the sheet, changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedetectColorsSheet(
    current: List<ColorEntity>,
    proposed: List<Pair<ColorEntity, Double>>,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        RedetectColorsContent(current = current, proposed = proposed, onApply = onApply, onCancel = onCancel)
    }
}

/** The body of [RedetectColorsSheet], split out so it can be previewed without a sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RedetectColorsContent(
    current: List<ColorEntity>,
    proposed: List<Pair<ColorEntity, Double>>,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp), // Extra padding for system bars
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.wardrobe_redetect_colors),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        RedetectColorGroup(title = stringResource(Res.string.wardrobe_redetect_current)) {
            if (current.isEmpty()) {
                AttributeEmptyHint()
            } else {
                current.forEach { color -> RedetectColorChip(name = color.name, hex = color.hex) }
            }
        }
        RedetectColorGroup(title = stringResource(Res.string.wardrobe_redetect_proposed)) {
            proposed.forEach { (color, share) ->
                RedetectColorChip(
                    name = color.name,
                    hex = color.hex,
                    share = stringResource(Res.string.wardrobe_redetect_share, (share * 100).roundToInt()),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.wardrobe_cancel)) }
            Button(onClick = onApply) { Text(stringResource(Res.string.wardrobe_redetect_apply)) }
        }
    }
}

/** A labelled group of colour chips in the re-detect sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedetectColorGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    }
}

/**
 * A read-only colour chip: a swatch of [hex] (outlined so light colours stay visible), the
 * colour [name], and an optional [share] label.
 */
@Composable
private fun RedetectColorChip(name: String, hex: String?, share: String? = null) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex) ?: MaterialTheme.colorScheme.outlineVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Text(text = name, style = MaterialTheme.typography.labelLarge)
            if (share != null) {
                Text(
                    text = share,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
