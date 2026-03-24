package com.closet.features.wardrobe

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.ui.util.IconMapper

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
    onEditSize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Size Attribute
        if (item.sizeValue != null) {
            AttributeSection(
                title = stringResource(R.string.wardrobe_size),
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
                    icon = Icons.Default.Straighten,
                    onClick = onEditSize
                )
            }
        }

        AttributeSection(
            title = stringResource(R.string.wardrobe_seasons),
            onEditClick = onEditSeasons
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.seasons.isEmpty()) {
                    AttributeEmptyHint()
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
            title = stringResource(R.string.wardrobe_colors),
            onEditClick = onEditColors
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.colors.isEmpty()) {
                    AttributeEmptyHint()
                } else {
                    item.colors.forEach { color ->
                        AttributeChip(
                            label = color.name,
                            color = color.hex?.let {
                                try { Color(android.graphics.Color.parseColor(it)) }
                                catch (_: Exception) { null }
                            },
                            onClick = onEditColors
                        )
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
                    AttributeEmptyHint()
                } else {
                    item.materials.forEach { material ->
                        AttributeChip(label = material.name, onClick = onEditMaterials)
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
                    AttributeEmptyHint()
                } else {
                    item.patterns.forEach { pattern ->
                        AttributeChip(
                            label = pattern.name,
                            iconResId = IconMapper.getIconResource(pattern.icon),
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
                    AttributeEmptyHint()
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

// ─── AttributeSection ─────────────────────────────────────────────────────────

/**
 * A layout wrapper for an attribute category (e.g., "Seasons").
 *
 * Displays a bold title and an edit icon button above the provided [content].
 *
 * @param title The section heading.
 * @param onEditClick Callback for the edit action.
 * @param content The attribute-specific UI (usually a FlowRow of chips).
 */
@Composable
internal fun AttributeSection(
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
                    contentDescription = stringResource(R.string.wardrobe_edit_with_section, title),
                    tint = MaterialTheme.colorScheme.primary
                )
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
 * Supports an optional leading icon (vector or resource) or a color swatch.
 *
 * @param label The text to display.
 * @param iconResId Optional drawable resource ID for the leading icon.
 * @param icon Optional [ImageVector] for the leading icon.
 * @param color Optional [Color] for a leading circular swatch.
 * @param onClick Callback when the chip is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttributeChip(
    label: String,
    iconResId: Int? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
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
        text = stringResource(R.string.wardrobe_none_selected),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
