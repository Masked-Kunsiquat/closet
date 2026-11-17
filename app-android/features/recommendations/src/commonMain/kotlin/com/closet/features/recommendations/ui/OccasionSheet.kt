package com.closet.features.recommendations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.OccasionEntity
import org.jetbrains.compose.resources.stringResource
import com.closet.features.recommendations.resources.Res
import com.closet.features.recommendations.resources.recs_occasion_sheet_empty
import com.closet.features.recommendations.resources.recs_occasion_sheet_prompt
import com.closet.features.recommendations.resources.recs_occasion_sheet_title
import com.closet.features.recommendations.resources.recs_sheet_skip

/**
 * Single-select bottom sheet that asks the user to pick an occasion for the
 * recommendation run. Both selection and dismissal are optional — the engine
 * runs without an occasion filter if the sheet is skipped.
 *
 * This composable is stateless. It carries no selected state internally;
 * tapping an occasion row calls [onSelected] immediately (no confirm button).
 *
 * @param occasions The full list of occasions loaded from [LookupDao] by the caller.
 * @param onSelected Called with the occasion [OccasionEntity.id] immediately on tap.
 * @param onSkip Called when the user taps the "Skip" text button.
 * @param onDismiss Called when the sheet is dismissed via swipe or back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OccasionSheet(
    occasions: List<OccasionEntity>,
    onSelected: (Long) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        OccasionSheetContent(
            occasions = occasions,
            onSelected = onSelected,
            onSkip = onSkip,
        )
    }
}

/**
 * Inner content for [OccasionSheet], extracted so Android Studio previews work
 * without a [ModalBottomSheet] wrapper.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OccasionSheetContent(
    occasions: List<OccasionEntity>,
    onSelected: (Long) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.recs_occasion_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider()

        // ── Occasion chips ────────────────────────────────────────────────────
        if (occasions.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.recs_occasion_sheet_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 4.dp,
                ),
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                occasions.forEach { occasion ->
                    FilterChip(
                        selected = false,
                        onClick = { onSelected(occasion.id) },
                        label = { Text(occasion.name) },
                    )
                }
            }
        } else {
            Text(
                text = stringResource(Res.string.recs_occasion_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        // ── Skip button ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSkip) {
                Text(stringResource(Res.string.recs_sheet_skip))
            }
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
