package com.closet.features.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.model.OutfitItemWithClothing
import java.io.File

/**
 * Smart outfit preview that selects its layout based on whether items have canvas positions.
 *
 * - **Grid layout** — used when all items have null posX/posY (V1 list-builder outfits).
 *   Shows up to 4 items in a 1/2/3/4-panel composition.
 * - **Collage layout** — used when any item has non-null posX or posY (future canvas outfits).
 *   Renders items at their stored coordinates within a clipped preview box.
 *
 * The interface is stable: swap the internals for a richer renderer without changing callers.
 */
@Composable
fun OutfitPreview(
    items: List<OutfitItemWithClothing>,
    resolveImagePath: (String?) -> File?,
    modifier: Modifier = Modifier
) {
    val hasCanvasLayout = items.any { it.outfitItem.posX != null || it.outfitItem.posY != null }
    if (hasCanvasLayout) {
        OutfitCollageLayout(items = items, resolveImagePath = resolveImagePath, modifier = modifier)
    } else {
        OutfitGridLayout(items = items, resolveImagePath = resolveImagePath, modifier = modifier)
    }
}

// ── Grid layout (V1) ─────────────────────────────────────────────────────────

@Composable
private fun OutfitGridLayout(
    items: List<OutfitItemWithClothing>,
    resolveImagePath: (String?) -> File?,
    modifier: Modifier = Modifier
) {
    val display = items.take(4)
    val overflow = items.size - display.size

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
    ) {
        when (display.size) {
            0 -> EmptyPreviewPlaceholder()

            1 -> PreviewImage(
                path = display[0].clothingItem.imagePath,
                resolveImagePath = resolveImagePath,
                modifier = Modifier.fillMaxSize()
            )

            2 -> Row(modifier = Modifier.fillMaxSize()) {
                PreviewImage(
                    path = display[0].clothingItem.imagePath,
                    resolveImagePath = resolveImagePath,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(1.dp))
                PreviewImage(
                    path = display[1].clothingItem.imagePath,
                    resolveImagePath = resolveImagePath,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            3 -> Row(modifier = Modifier.fillMaxSize()) {
                PreviewImage(
                    path = display[0].clothingItem.imagePath,
                    resolveImagePath = resolveImagePath,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(1.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    PreviewImage(
                        path = display[1].clothingItem.imagePath,
                        resolveImagePath = resolveImagePath,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    PreviewImage(
                        path = display[2].clothingItem.imagePath,
                        resolveImagePath = resolveImagePath,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    PreviewImage(
                        path = display[0].clothingItem.imagePath,
                        resolveImagePath = resolveImagePath,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    PreviewImage(
                        path = display[1].clothingItem.imagePath,
                        resolveImagePath = resolveImagePath,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(modifier = Modifier.weight(1f)) {
                    PreviewImage(
                        path = display[2].clothingItem.imagePath,
                        resolveImagePath = resolveImagePath,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PreviewImage(
                            path = display[3].clothingItem.imagePath,
                            resolveImagePath = resolveImagePath,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (overflow > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$overflow",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Collage layout (future canvas outfits) ────────────────────────────────────

@Composable
private fun OutfitCollageLayout(
    items: List<OutfitItemWithClothing>,
    resolveImagePath: (String?) -> File?,
    modifier: Modifier = Modifier
) {
    // Items sorted by zIndex so higher layers draw on top.
    val sorted = items.sortedBy { it.outfitItem.zIndex ?: 0 }
    val itemSizeDp = 72.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
    ) {
        sorted.forEach { entry ->
            val posX = entry.outfitItem.posX ?: 0f
            val posY = entry.outfitItem.posY ?: 0f
            val scale = entry.outfitItem.scale ?: 1f

            AsyncImage(
                model = resolveImagePath(entry.clothingItem.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(itemSizeDp)
                    .absoluteOffset(x = posX.dp, y = posY.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun PreviewImage(
    path: String?,
    resolveImagePath: (String?) -> File?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = resolveImagePath(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun EmptyPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
