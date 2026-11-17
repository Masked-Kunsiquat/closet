package com.closet.features.wardrobe

import com.closet.features.wardrobe.resources.Res
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.core.ui.resources.ic_symbol_straighten
import com.closet.features.wardrobe.resources.wardrobe_field_size_system
import com.closet.features.wardrobe.resources.wardrobe_filter_clear_all
import com.closet.features.wardrobe.resources.wardrobe_filter_section_color
import com.closet.features.wardrobe.resources.wardrobe_filter_section_occasion
import com.closet.features.wardrobe.resources.wardrobe_filter_section_season
import com.closet.features.wardrobe.resources.wardrobe_filter_title
import com.closet.core.ui.format.parseHexColor
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * @param colors All available colors for filtering.
 * @param seasons All available seasons for filtering.
 * @param occasions All available occasions for filtering.
 * @param sizeSystems All available size systems for filtering.
 * @param selectedColorIds The set of currently selected color IDs.
 * @param selectedSeasonIds The set of currently selected season IDs.
 * @param selectedOccasionIds The set of currently selected occasion IDs.
 * @param selectedSizeSystemIds The set of currently selected size system IDs.
 * @param onToggleColor Callback to toggle a color filter.
 * @param onToggleSeason Callback to toggle a season filter.
 * @param onToggleOccasion Callback to toggle an occasion filter.
 * @param onToggleSizeSystem Callback to toggle a size system filter.
 * @param onClearAll Callback to reset all advanced filters in this panel.
 * @param onDismiss Callback to close the bottom sheet.
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
                    text = stringResource(Res.string.wardrobe_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearAll, enabled = anyActive) {
                    Text(stringResource(Res.string.wardrobe_filter_clear_all))
                }
            }

            HorizontalDivider()

            // ── Scrollable sections ───────────────────────────────────────────
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (colors.isNotEmpty()) {
                    FilterSection(title = stringResource(Res.string.wardrobe_filter_section_color)) {
                        colors.forEach { color ->
                            val fallback = MaterialTheme.colorScheme.outlineVariant
                            val swatchColor = remember(color.hex) {
                                parseHexColor(color.hex) ?: fallback
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
                    FilterSection(title = stringResource(Res.string.wardrobe_field_size_system)) {
                        sizeSystems.forEach { system ->
                            FilterChip(
                                selected = system.id in selectedSizeSystemIds,
                                onClick = { onToggleSizeSystem(system.id) },
                                label = { Text(system.name) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.ic_symbol_straighten),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (seasons.isNotEmpty()) {
                    FilterSection(title = stringResource(Res.string.wardrobe_filter_section_season)) {
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
                    FilterSection(title = stringResource(Res.string.wardrobe_filter_section_occasion)) {
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
 *
 * @param title The label for the section.
 * @param modifier Modifier for the container.
 * @param content The chips or other UI elements to display in the section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterSection(
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
