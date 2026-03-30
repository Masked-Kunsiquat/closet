package com.closet.features.recommendations.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.recommendations.R
import com.closet.features.recommendations.engine.EngineItem
import com.closet.features.recommendations.engine.OutfitCombo
import java.io.File

/**
 * A card representing one recommended outfit combination in the carousel.
 *
 * Displays a 2-column grid of item images (or placeholder icons when no image
 * is available), a list of item names below the grid, and three action buttons
 * at the bottom.
 *
 * When [combo.isAiSelected] is true and [combo.reason] is non-null, a small
 * "AI pick — why?" text button is shown below the item name list. Tapping it
 * expands an inline text block with the AI's reason. Tapping again collapses it.
 * The expanded/collapsed state is local to this composable — no ViewModel state needed.
 *
 * This composable is stateless for all actions — they are delegated to the caller.
 *
 * @param combo           The outfit combination to display.
 * @param resolveImage    Maps a relative [EngineItem.imagePath] to a [File] for Coil,
 *                        or returns null when no image exists for that path.
 * @param logItEnabled    When false, the "Log it" button is disabled. Set to false until
 *                        [OutfitBuilderDestination] supports pre-selected item IDs.
 * @param onLogIt         Called when the user taps "Log it".
 * @param onSaveForLater  Called when the user taps "Save for later".
 * @param onRegenerate    Called when the user taps "Regenerate".
 * @param modifier        Optional [Modifier].
 */
@Composable
fun OutfitComboCard(
    combo: OutfitCombo,
    resolveImage: (String?) -> File?,
    logItEnabled: Boolean = false,
    onLogIt: () -> Unit,
    onSaveForLater: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local state for the "why?" expand/collapse — not surfaced to the ViewModel.
    // Keyed on combo so the state resets when the pager swipes to a different combo.
    var reasonExpanded by remember(key1 = combo) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Item image grid ───────────────────────────────────────────────
            ItemImageGrid(
                items = combo.items,
                resolveImage = resolveImage,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Item name list ────────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                combo.items.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── AI pick — "why?" affordance ───────────────────────────────────
            // Shown only when the combo was selected by the AI scorer AND a reason
            // was returned. Hidden by default; tapping toggles the inline reason text.
            if (combo.isAiSelected && combo.reason != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { reasonExpanded = !reasonExpanded },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text(
                            text = if (reasonExpanded) {
                                stringResource(R.string.recs_ai_pick_collapse)
                            } else {
                                stringResource(R.string.recs_ai_pick_badge)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AnimatedVisibility(visible = reasonExpanded) {
                        Text(
                            text = combo.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Button(
                onClick = onLogIt,
                enabled = logItEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recs_action_log_it))
            }

            OutlinedButton(
                onClick = onSaveForLater,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recs_action_save_for_later))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onRegenerate) {
                    Text(stringResource(R.string.recs_action_regenerate))
                }
            }
        }
    }
}

/**
 * 2-column grid of item images for an outfit combo.
 *
 * Each cell is square. When an item has no image path or the file cannot be
 * resolved, a placeholder icon is shown instead.
 */
@Composable
private fun ItemImageGrid(
    items: List<EngineItem>,
    resolveImage: (String?) -> File?,
    modifier: Modifier = Modifier,
) {
    // LazyVerticalGrid requires a fixed height when nested inside a Column.
    // Use BoxWithConstraints to measure available width, derive cell size from it
    // (2 columns with 8dp gap), then compute exact grid height so cells remain square.
    val cellSpacing = 8.dp
    val rowCount = (items.size + 1) / 2

    BoxWithConstraints(modifier = modifier) {
        val cellSize = (maxWidth - cellSpacing) / 2
        val gridHeight = cellSize * rowCount + cellSpacing * (rowCount - 1).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(gridHeight),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(cellSpacing),
            verticalArrangement = Arrangement.spacedBy(cellSpacing),
            userScrollEnabled = false,
        ) {
            items(items) { item ->
                ItemImageCell(
                    item = item,
                    resolveImage = resolveImage,
                )
            }
        }
    }
}

/**
 * A single square image cell. Shows the item image when available, or a
 * placeholder [Icon] when the image path is null/blank or unresolvable.
 */
@Composable
private fun ItemImageCell(
    item: EngineItem,
    resolveImage: (String?) -> File?,
    modifier: Modifier = Modifier,
) {
    val imageFile = resolveImage(item.imagePath)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = stringResource(
                    R.string.recs_combo_item_image_description,
                    item.name,
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val previewItems = listOf(
    EngineItem(
        id = 1L,
        name = "White Oxford Shirt",
        imagePath = null,
        categoryId = 1L,
        subcategoryId = null,
        outfitRole = "Top",
        warmthLayer = "Base",
        colorFamilies = setOf("Neutral"),
        isPatternSolid = true,
    ),
    EngineItem(
        id = 2L,
        name = "Slim Chinos",
        imagePath = null,
        categoryId = 2L,
        subcategoryId = null,
        outfitRole = "Bottom",
        warmthLayer = "None",
        colorFamilies = setOf("Earth"),
        isPatternSolid = true,
    ),
    EngineItem(
        id = 3L,
        name = "White Sneakers",
        imagePath = null,
        categoryId = 6L,
        subcategoryId = null,
        outfitRole = "Footwear",
        warmthLayer = "None",
        colorFamilies = setOf("Neutral"),
        isPatternSolid = true,
    ),
)

private val previewCombo = OutfitCombo(
    items = previewItems,
    score = 0.87,
)

private val previewAiCombo = OutfitCombo(
    items = previewItems,
    score = 0.91,
    isAiSelected = true,
    reason = "Neutral tones and a single earth accent create a clean, cohesive look suited to today's mild conditions.",
)

@Preview(showBackground = true, name = "OutfitComboCard - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "OutfitComboCard - Dark",
)
@Composable
private fun OutfitComboCardPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OutfitComboCard(
                combo = previewCombo,
                resolveImage = { null },
                onLogIt = {},
                onSaveForLater = {},
                onRegenerate = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "OutfitComboCard - AI pick (Light)")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "OutfitComboCard - AI pick (Dark)",
)
@Composable
private fun OutfitComboCardAiPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OutfitComboCard(
                combo = previewAiCombo,
                resolveImage = { null },
                onLogIt = {},
                onSaveForLater = {},
                onRegenerate = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
