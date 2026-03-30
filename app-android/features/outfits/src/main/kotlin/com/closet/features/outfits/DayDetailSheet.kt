package com.closet.features.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.dao.OutfitLogWithMeta
import com.closet.core.data.model.TemperatureUnit
import com.closet.core.data.model.WeatherCondition
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet that shows all outfit logs for a single calendar day.
 *
 * Displayed when the user taps a logged day in the Journal calendar.
 * Allows toggling OOTD status and deleting individual log entries.
 *
 * @param date ISO date string ("YYYY-MM-DD") for the selected day.
 * @param logs All [OutfitLogWithMeta] entries for [date].
 * @param onDismiss Called when the sheet should be closed (back gesture or tap-outside).
 * @param onAddLog Called when the user taps "Log outfit" — opens the outfit picker.
 * @param onEditLog Called with the tapped log entry — opens the log-edit sheet.
 * @param onOotdToggle Called with (logId, currentIsOotd) when the crown icon is tapped.
 * @param onDeleteLog Called with logId when the delete icon is tapped.
 * @param resolveImage Converts a relative image path to an absolute [File] for Coil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailSheet(
    date: String,
    logs: List<OutfitLogWithMeta>,
    onDismiss: () -> Unit,
    onAddLog: () -> Unit,
    onEditLog: (OutfitLogWithMeta) -> Unit,
    onOotdToggle: (logId: Long, currentIsOotd: Boolean) -> Unit,
    onDeleteLog: (logId: Long) -> Unit,
    resolveImage: (String?) -> File?,
    temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var logPendingDelete by remember { mutableStateOf<Long?>(null) }

    logPendingDelete?.let { logId ->
        var isDeleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { logPendingDelete = null },
            title = { Text(stringResource(R.string.journal_delete_log_confirm_title)) },
            text = { Text(stringResource(R.string.journal_delete_log_confirm_message)) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        if (isDeleting) return@TextButton
                        isDeleting = true
                        logPendingDelete = null
                        onDeleteLog(logId)
                    },
                ) {
                    Text(stringResource(R.string.outfits_actions_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { logPendingDelete = null }) {
                    Text(stringResource(R.string.outfits_actions_cancel))
                }
            },
        )
    }

    val headerLabel = remember(date) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headerLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Button(
                onClick = onAddLog,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.journal_log_outfit))
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.journal_day_no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCard(
                        log = log,
                        imageFile = resolveImage(log.coverImage),
                        temperatureUnit = temperatureUnit,
                        onClick = { onEditLog(log) },
                        onOotdToggle = { onOotdToggle(log.id, log.isOotd == 1) },
                        onDelete = { logPendingDelete = log.id },
                    )
                }
            }
        }
    }
}

// ─── Log entry card ───────────────────────────────────────────────────────────

@Composable
private fun LogCard(
    log: OutfitLogWithMeta,
    imageFile: File?,
    temperatureUnit: TemperatureUnit,
    onClick: () -> Unit,
    onOotdToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOotd = log.isOotd == 1

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover image thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageFile != null) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Outfit name + item count + notes/weather summary
            Column(Modifier.weight(1f)) {
                Text(
                    text = log.outfitName ?: stringResource(R.string.outfits_gallery_untitled),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.outfits_gallery_item_count, log.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Weather row: condition icon + label + temp range (read-only, stored on log)
                val condition = WeatherCondition.fromString(log.weatherCondition)
                val tempLow = log.temperatureLow
                val tempHigh = log.temperatureHigh
                val tempText = when {
                    tempLow != null && tempHigh != null ->
                        "${tempLow.toDisplayTemp(temperatureUnit)} / ${tempHigh.toDisplayTemp(temperatureUnit)}"
                    tempLow != null -> tempLow.toDisplayTemp(temperatureUnit)
                    tempHigh != null -> tempHigh.toDisplayTemp(temperatureUnit)
                    else -> null
                }
                val weatherText = listOfNotNull(condition?.label, tempText).joinToString(" · ")
                if (condition != null || tempText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (condition != null) {
                            Icon(
                                imageVector = condition.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (weatherText.isNotEmpty()) {
                            Text(
                                text = weatherText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val notes = log.notes
                if (notes != null) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // OOTD crown toggle
            IconButton(onClick = onOotdToggle) {
                Icon(
                    imageVector = if (isOotd) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (isOotd) R.string.journal_ootd_unset else R.string.journal_ootd_set,
                    ),
                    tint = if (isOotd) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Delete
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.journal_delete_log),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
