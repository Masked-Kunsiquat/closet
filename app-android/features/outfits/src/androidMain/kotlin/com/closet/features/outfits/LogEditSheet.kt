package com.closet.features.outfits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.WeatherCondition

/**
 * Bottom sheet for editing an existing log entry's notes and weather condition.
 *
 * @param log The log entry being edited; used to initialise form state.
 * @param onDismiss Called when the sheet is dismissed without saving.
 * @param onSave Called with the updated (notes, weatherCondition) when the user saves.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LogEditSheet(
    log: OutfitLogWithMeta,
    onDismiss: () -> Unit,
    onSave: (notes: String?, weatherCondition: WeatherCondition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local form state, keyed on log.id so a different entry starts fresh.
    var notes by rememberSaveable(log.id) { mutableStateOf(log.notes ?: "") }
    var selectedWeather by remember(log.id) {
        mutableStateOf(WeatherCondition.fromString(log.weatherCondition))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Title + outfit name
            Column {
                Text(
                    text = stringResource(R.string.journal_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val outfitName = log.outfitName
                if (outfitName != null) {
                    Text(
                        text = outfitName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.journal_edit_notes_label)) },
                placeholder = { Text(stringResource(R.string.journal_edit_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            // Weather condition chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.journal_edit_weather_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WeatherCondition.entries.forEach { condition ->
                        val selected = selectedWeather == condition
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedWeather = if (selected) null else condition
                            },
                            label = { Text(condition.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = condition.icon(),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.journal_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onSave(notes.ifBlank { null }, selectedWeather) },
                ) {
                    Text(stringResource(R.string.journal_edit_save))
                }
            }
        }
    }
}

