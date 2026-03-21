package com.closet.features.outfits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.OutfitWithItems
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet that lets the user pick a saved outfit to log on [date].
 *
 * Opens from [DayDetailSheet] when the user taps "Log outfit". Dismissing it
 * (back gesture, tap-outside, or the back arrow) returns to the day detail sheet.
 *
 * @param date ISO date string ("YYYY-MM-DD") for the day being logged.
 * @param outfits All saved outfits to pick from.
 * @param onDismiss Called when the sheet should close without logging anything.
 * @param onOutfitSelected Called with the chosen outfit's ID to log it and close.
 * @param resolveImage Converts a relative image path to an absolute [File] for Coil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitPickerForDate(
    date: String,
    outfits: List<OutfitWithItems>,
    onDismiss: () -> Unit,
    onOutfitSelected: (outfitId: Long) -> Unit,
    resolveImage: (String?) -> File?,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val headerLabel = remember(date) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM d"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        // Header row: back arrow + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.journal_picker_back),
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.journal_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = headerLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (outfits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.journal_picker_empty),
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
                items(outfits, key = { it.outfit.id }) { outfitWithItems ->
                    PickerOutfitCard(
                        outfitWithItems = outfitWithItems,
                        resolveImage = resolveImage,
                        onClick = { onOutfitSelected(outfitWithItems.outfit.id) },
                    )
                }
            }
        }
    }
}

// ─── Outfit card for the picker ───────────────────────────────────────────────

@Composable
private fun PickerOutfitCard(
    outfitWithItems: OutfitWithItems,
    resolveImage: (String?) -> File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        OutfitPreview(
            items = outfitWithItems.items,
            resolveImagePath = resolveImage,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outfitWithItems.outfit.name
                        ?: stringResource(R.string.outfits_gallery_untitled),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(
                        R.string.outfits_gallery_item_count,
                        outfitWithItems.items.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
