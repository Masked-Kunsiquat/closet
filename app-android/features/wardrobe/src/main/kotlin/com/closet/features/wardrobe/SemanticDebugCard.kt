package com.closet.features.wardrobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Debug-only card that surfaces `semantic_description` and `image_caption` for a clothing item.
 *
 * Tapping opens a [ModalBottomSheet] where both fields are shown as read-only text with
 * a Copy button. Intended for data-quality inspection during Phase 1 iteration.
 *
 * **Only rendered when [BuildConfig.DEBUG] is true — never shown in release builds.**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SemanticDebugCard(
    semanticDescription: String?,
    imageCaption: String?,
) {
    var showSheet by remember { mutableStateOf(false) }

    OutlinedCard(
        onClick = { showSheet = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge { Text("DEV") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Semantic data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            SemanticDebugSheetContent(
                semanticDescription = semanticDescription,
                imageCaption = imageCaption,
            )
        }
    }
}

@Composable
private fun SemanticDebugSheetContent(
    semanticDescription: String?,
    imageCaption: String?,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Semantic data inspector",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        DebugTextField(
            label = "Semantic description",
            value = semanticDescription,
            onCopy = { clipboardManager.setText(AnnotatedString(semanticDescription ?: "")) },
        )
        Spacer(Modifier.height(12.dp))
        DebugTextField(
            label = "Image caption",
            value = imageCaption,
            onCopy = { clipboardManager.setText(AnnotatedString(imageCaption ?: "")) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DebugTextField(
    label: String,
    value: String?,
    onCopy: () -> Unit,
) {
    OutlinedTextField(
        value = value ?: "Not yet generated",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        textStyle = LocalTextStyle.current.copy(
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
        ),
        trailingIcon = {
            if (value != null) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
}
