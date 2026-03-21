package com.closet.features.wardrobe

import android.content.res.Configuration
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.closet.core.ui.theme.ClosetTheme

/**
 * Generic multi-select item for the bottom sheet.
 */
data class MultiSelectItem<out T>(
    val id: Long,
    val label: String,
    val original: T,
    val colorHex: String? = null,
    val iconResId: Int? = null
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
                    Text(stringResource(R.string.wardrobe_save))
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
                        iconResId = item.iconResId,
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
private fun MultiSelectRow(
    label: String,
    colorHex: String?,
    iconResId: Int?,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onToggle() },
        headlineContent = { Text(label) },
        leadingContent = {
            if (colorHex != null) {
                val color = try {
                    Color(colorHex.toColorInt())
                } catch (e: Exception) {
                    Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (colorHex.isEmpty()) Modifier.background(Color.Gray) else Modifier)
                )
            } else if (iconResId != null) {
                Icon(
                    painter = painterResource(id = iconResId),
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

// region Previews

private val previewColorItems = listOf(
    MultiSelectItem(1L, "Black", Unit, colorHex = "#000000"),
    MultiSelectItem(2L, "White", Unit, colorHex = "#FFFFFF"),
    MultiSelectItem(3L, "Navy", Unit, colorHex = "#001F5B"),
    MultiSelectItem(4L, "Red", Unit, colorHex = "#CC0000"),
    MultiSelectItem(5L, "Olive", Unit, colorHex = "#708238"),
)

private val previewLabelItems = listOf(
    MultiSelectItem(1L, "Spring", Unit),
    MultiSelectItem(2L, "Summer", Unit),
    MultiSelectItem(3L, "Fall", Unit),
    MultiSelectItem(4L, "Winter", Unit),
)

@Composable
private fun MultiSelectSheetContentPreview(
    title: String,
    items: List<MultiSelectItem<Unit>>,
    selectedIds: Set<Long>
) {
    // Render the inner sheet content directly (ModalBottomSheet not renderable in preview)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = {}) { Text("Save") }
            }
            HorizontalDivider()
            items.forEach { item ->
                MultiSelectRow(
                    label = item.label,
                    colorHex = item.colorHex,
                    iconResId = item.iconResId,
                    isSelected = item.id in selectedIds,
                    onToggle = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Color Picker - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Color Picker - Dark")
@Composable
private fun MultiSelectSheetColorsPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Colors",
            items = previewColorItems,
            selectedIds = setOf(1L, 3L)
        )
    }
}

@Preview(showBackground = true, name = "Label Picker - Partial Selection")
@Composable
private fun MultiSelectSheetLabelsPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Seasons",
            items = previewLabelItems,
            selectedIds = setOf(2L)
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun MultiSelectSheetEmptyPreview() {
    ClosetTheme {
        MultiSelectSheetContentPreview(
            title = "Materials",
            items = emptyList(),
            selectedIds = emptySet()
        )
    }
}

// endregion
