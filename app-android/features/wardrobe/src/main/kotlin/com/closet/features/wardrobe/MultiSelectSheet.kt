package com.closet.features.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Generic multi-select item for the bottom sheet.
 */
data class MultiSelectItem<T>(
    val id: Long,
    val label: String,
    val original: T,
    val colorHex: String? = null
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
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onToggle() },
        headlineContent = { Text(label) },
        leadingContent = colorHex?.let {
            {
                val color = try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (it.isEmpty()) Modifier.background(Color.Gray) else Modifier)
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
