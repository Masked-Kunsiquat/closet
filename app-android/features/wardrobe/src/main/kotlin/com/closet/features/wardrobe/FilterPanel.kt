package com.closet.features.wardrobe

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.OccasionEntity
import com.closet.core.data.model.SeasonEntity
import com.closet.core.data.model.SizeSystemEntity
import com.closet.core.ui.theme.ClosetTheme

/**
 * Bottom sheet filter panel for the Closet screen.
 *
 * Shows multiple [FlowRow] chip sections — Color, Season, Occasion, and Size System —
 * allowing multi-select filtering across these dimensions simultaneously.
 *
 * @param sheetState Controls sheet expansion; defaults to [rememberModalBottomSheetState].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterPanel(
    colors: List<ColorEntity>,
    seasons: List<SeasonEntity>,
    occasions: List<OccasionEntity>,
    sizeSystems: List<SizeSystemEntity>,
    selectedColorIds: Set<Long>,
    selectedSeasonIds: Set<Long>,
    selectedOccasionIds: Set<Long>,
    selectedSizeSystemIds: Set<Long>,
    onToggleColor: (Long) -> Unit,
    onToggleSeason: (Long) -> Unit,
    onToggleOccasion: (Long) -> Unit,
    onToggleSizeSystem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val anyActive = selectedColorIds.isNotEmpty() ||
            selectedSeasonIds.isNotEmpty() ||
            selectedOccasionIds.isNotEmpty() ||
            selectedSizeSystemIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearAll, enabled = anyActive) {
                    Text(stringResource(R.string.wardrobe_filter_clear_all))
                }
            }

            HorizontalDivider()

            // ── Scrollable sections ───────────────────────────────────────────
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (colors.isNotEmpty()) {
                    FilterSection(title = stringResource(R.string.wardrobe_filter_section_color)) {
                        colors.forEach { color ->
                            val fallback = MaterialTheme.colorScheme.outlineVariant
                            val swatchColor = remember(color.hex) {
                                color.hex?.let {
                                    runCatching { Color(android.graphics.Color.parseColor(it)) }
                                        .getOrNull()
                                } ?: fallback
                            }
                            FilterChip(
                                selected = color.id in selectedColorIds,
                                onClick = { onToggleColor(color.id) },
                                label = { Text(color.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                    )
                                }
                            )
                        }
                    }
                }

                if (sizeSystems.isNotEmpty()) {
                    FilterSection(title = stringResource(R.string.wardrobe_field_size_system)) {
                        sizeSystems.forEach { system ->
                            FilterChip(
                                selected = system.id in selectedSizeSystemIds,
                                onClick = { onToggleSizeSystem(system.id) },
                                label = { Text(system.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Straighten,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (seasons.isNotEmpty()) {
                    FilterSection(title = stringResource(R.string.wardrobe_filter_section_season)) {
                        seasons.forEach { season ->
                            FilterChip(
                                selected = season.id in selectedSeasonIds,
                                onClick = { onToggleSeason(season.id) },
                                label = { Text(season.name) }
                            )
                        }
                    }
                }

                if (occasions.isNotEmpty()) {
                    FilterSection(title = stringResource(R.string.wardrobe_filter_section_occasion)) {
                        occasions.forEach { occasion ->
                            FilterChip(
                                selected = occasion.id in selectedOccasionIds,
                                onClick = { onToggleOccasion(occasion.id) },
                                label = { Text(occasion.name) }
                            )
                        }
                    }
                }

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * Single labeled section inside [FilterPanel]. Renders a [Text] title above a
 * [FlowRow] of chips that wraps automatically across lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() }
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val filterPreviewColors = listOf(
    ColorEntity(1L, "Black", "#1C1C1C"),
    ColorEntity(2L, "White", "#F5F5F5"),
    ColorEntity(3L, "Navy", "#1B2A4A"),
    ColorEntity(4L, "Olive", "#6B7C3A"),
    ColorEntity(5L, "Cream", "#F5F0E8"),
    ColorEntity(6L, "Burgundy", "#7C1C2A"),
)

private val previewSeasons = listOf(
    SeasonEntity(1L, "Spring", null),
    SeasonEntity(2L, "Summer", null),
    SeasonEntity(3L, "Fall", null),
    SeasonEntity(4L, "Winter", null),
)

private val previewOccasions = listOf(
    OccasionEntity(1L, "Casual", null),
    OccasionEntity(2L, "Work", null),
    OccasionEntity(3L, "Evening", null),
    OccasionEntity(4L, "Sport", null),
    OccasionEntity(5L, "Beach", null),
)

private val previewSizeSystems = listOf(
    SizeSystemEntity(1L, "Letter"),
    SizeSystemEntity(2L, "Women's Numeric"),
    SizeSystemEntity(3L, "Shoes (US Men's)"),
    SizeSystemEntity(4L, "One Size"),
)

/**
 * Renders the filter panel content directly (without [ModalBottomSheet]) so
 * Android Studio can display a pixel-accurate preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanelContentPreview(
    selectedColorIds: Set<Long> = emptySet(),
    selectedSeasonIds: Set<Long> = emptySet(),
    selectedOccasionIds: Set<Long> = emptySet(),
    selectedSizeSystemIds: Set<Long> = emptySet(),
) {
    val anyActive = selectedColorIds.isNotEmpty() ||
            selectedSeasonIds.isNotEmpty() ||
            selectedOccasionIds.isNotEmpty() ||
            selectedSizeSystemIds.isNotEmpty()

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {}, enabled = anyActive) {
                    Text(stringResource(R.string.wardrobe_filter_clear_all))
                }
            }
            HorizontalDivider()
            FilterSection(title = stringResource(R.string.wardrobe_filter_section_color)) {
                filterPreviewColors.forEach { color ->
                    val fallback = MaterialTheme.colorScheme.outlineVariant
                    val swatchColor = remember(color.hex) {
                        color.hex?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                        } ?: fallback
                    }
                    FilterChip(
                        selected = color.id in selectedColorIds,
                        onClick = {},
                        label = { Text(color.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )
                        }
                    )
                }
            }
            FilterSection(title = stringResource(R.string.wardrobe_field_size_system)) {
                previewSizeSystems.forEach { system ->
                    FilterChip(
                        selected = system.id in selectedSizeSystemIds,
                        onClick = {},
                        label = { Text(system.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
            FilterSection(title = stringResource(R.string.wardrobe_filter_section_season)) {
                previewSeasons.forEach { season ->
                    FilterChip(
                        selected = season.id in selectedSeasonIds,
                        onClick = {},
                        label = { Text(season.name) }
                    )
                }
            }
            FilterSection(title = stringResource(R.string.wardrobe_filter_section_occasion)) {
                previewOccasions.forEach { occasion ->
                    FilterChip(
                        selected = occasion.id in selectedOccasionIds,
                        onClick = {},
                        label = { Text(occasion.name) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Filter Panel - No Selection - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Filter Panel - No Selection - Dark")
@Composable
private fun FilterPanelEmptyPreview() {
    ClosetTheme { FilterPanelContentPreview() }
}

@Preview(showBackground = true, name = "Filter Panel - Active Selections")
@Composable
private fun FilterPanelActivePreview() {
    ClosetTheme {
        FilterPanelContentPreview(
            selectedColorIds = setOf(1L, 3L),
            selectedSeasonIds = setOf(2L),
            selectedSizeSystemIds = setOf(1L),
        )
    }
}
