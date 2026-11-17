package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_check
import com.closet.core.ui.resources.ic_symbol_close
import com.closet.core.ui.resources.ic_symbol_delete
import com.closet.core.ui.resources.ic_symbol_edit
import com.closet.features.wardrobe.resources.wardrobe_cancel
import com.closet.features.wardrobe.resources.wardrobe_delete
import com.closet.features.wardrobe.resources.wardrobe_edit
import com.closet.features.wardrobe.resources.wardrobe_save
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import org.jetbrains.compose.resources.stringResource
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
                    Icon(painterResource(CoreRes.drawable.ic_symbol_edit), contentDescription = stringResource(Res.string.wardrobe_edit))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painterResource(CoreRes.drawable.ic_symbol_delete),
                        contentDescription = stringResource(Res.string.wardrobe_delete),
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
            Icon(painterResource(CoreRes.drawable.ic_symbol_check), contentDescription = stringResource(Res.string.wardrobe_save))
        }
        IconButton(onClick = onCancel, enabled = !isSaving) {
            Icon(painterResource(CoreRes.drawable.ic_symbol_close), contentDescription = stringResource(Res.string.wardrobe_cancel))
        }
    }
}
