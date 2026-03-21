package com.closet.features.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.BrandEntity

// ─── Editor state ─────────────────────────────────────────────────────────────

internal sealed class BrandEditorState {
    data object Idle : BrandEditorState()
    data class Adding(val text: String = "", val saving: Boolean = false) : BrandEditorState()
    data class Editing(val brandId: Long, val text: String, val saving: Boolean = false) : BrandEditorState()
}

// ─── BrandRow ─────────────────────────────────────────────────────────────────

@Composable
internal fun BrandRow(
    brand: BrandEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(brand.name) },
        trailingContent = {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.wardrobe_edit))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.wardrobe_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

// ─── BrandInputRow ────────────────────────────────────────────────────────────

@Composable
internal fun BrandInputRow(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    isSaving: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && !isSaving) onConfirm() })
        )
        IconButton(onClick = onConfirm, enabled = text.isNotBlank() && !isSaving) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.wardrobe_save))
        }
        IconButton(onClick = onCancel, enabled = !isSaving) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wardrobe_cancel))
        }
    }
}
