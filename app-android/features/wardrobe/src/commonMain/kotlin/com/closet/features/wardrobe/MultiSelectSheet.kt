package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import com.closet.features.wardrobe.resources.wardrobe_save
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.ui.format.parseHexColor
import com.closet.core.ui.theme.ClosetTheme

/**
 * Generic multi-select item for the bottom sheet.
 */
data class MultiSelectItem<out T>(
    val id: Long,
    val label: String,
    val original: T,
    val colorHex: String? = null,
    val icon: DrawableResource? = null
)

/**
 * A reusable Bottom Sheet for multi-selecting attributes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectSheet(
    title: String,
    items: List<MultiSelectItem<T>>,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    var currentSelection by remember(selectedIds) { mutableStateOf(selectedIds) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp) // Extra padding for system bars
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onConfirm(currentSelection.toList()) }) {
                    Text(stringResource(Res.string.wardrobe_save))
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(items) { item ->
                    MultiSelectRow(
                        label = item.label,
                        colorHex = item.colorHex,
                        icon = item.icon,
                        isSelected = currentSelection.contains(item.id),
                        onToggle = {
                            currentSelection = if (currentSelection.contains(item.id)) {
                                currentSelection - item.id
                            } else {
                                currentSelection + item.id
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MultiSelectRow(
    label: String,
    colorHex: String?,
    icon: DrawableResource?,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onToggle() },
        headlineContent = { Text(label) },
        leadingContent = {
            if (colorHex != null) {
                val fallback = MaterialTheme.colorScheme.outlineVariant
                val color = parseHexColor(colorHex) ?: fallback
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            } else if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    )
}
