package com.closet.features.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.dao.StatItem
import java.io.File

// ─── Section header ───────────────────────────────────────────────────────────

/** Standard title used at the top of every stats section. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

// ─── Headline card ────────────────────────────────────────────────────────────

/**
 * Single metric card used in [HeadlineCardsRow] and [WashStatusSection].
 * Shows a large [value] above a small [label], with full accessibility support.
 */
@Composable
internal fun StatHeadlineCard(
    value: String,
    label: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityLabel
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var fontSize by remember(value) { mutableStateOf(24.sp) }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = fontSize),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { if (it.didOverflowWidth) fontSize = (fontSize.value * 0.85f).sp }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Multi-layer progress bar ─────────────────────────────────────────────────

/**
 * A two-layer horizontal progress bar for displaying two related metrics on the same scale.
 *
 * Renders [secondaryProgress] as a muted background fill behind [primaryProgress], making
 * the relationship between the two values immediately visible — e.g. items owned (secondary)
 * vs items worn (primary) for a given category.
 *
 * Both values should be pre-normalized to [0f, 1f] against the same max
 * (e.g. `max(maxItemCount, maxWearCount)`).
 */
@Composable
internal fun MultipleLinearProgressIndicator(
    primaryProgress: Float,
    secondaryProgress: Float,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.primaryContainer,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    clipShape: Shape = RoundedCornerShape(3.dp)
) {
    Box(
        modifier = modifier
            .clip(clipShape)
            .background(backgroundColor)
            .height(6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(secondaryColor)
                .fillMaxHeight()
                .fillMaxWidth(secondaryProgress)
        )
        Box(
            modifier = Modifier
                .background(primaryColor)
                .fillMaxHeight()
                .fillMaxWidth(primaryProgress)
        )
    }
}

// ─── Never worn ───────────────────────────────────────────────────────────────

/** Collapsible section listing every active item that has never appeared in an outfit log. */
@Composable
internal fun NeverWornSection(
    items: List<StatItem>,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.stats_section_never_worn_count, items.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(
                if (expanded) R.string.stats_never_worn_collapse else R.string.stats_never_worn_expand
            )
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_never_worn_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                items.forEach { item ->
                    NeverWornRow(item, resolveImagePath, onItemClick)
                }
            }
        }
    }
}

/** Single 48 dp thumbnail row inside [NeverWornSection]. Tapping navigates to the item detail screen. */
@Composable
private fun NeverWornRow(
    item: StatItem,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(item.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = resolveImagePath(item.imagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Empty states ─────────────────────────────────────────────────────────────

/** Full-column empty state shown when the wardrobe has no items at all. */
@Composable
internal fun StatsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Checkroom,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.stats_empty_no_items_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_empty_no_items_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Informational card shown when items exist but no outfits have been logged yet.
 * Replaces the wear-based sections (most worn, cost per wear, category wear).
 */
@Composable
internal fun NoLogsInfoCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.stats_no_logs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stats_no_logs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
