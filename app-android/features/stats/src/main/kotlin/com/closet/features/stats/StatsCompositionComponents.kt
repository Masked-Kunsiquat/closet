package com.closet.features.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CategorySubcategoryRow
import com.closet.core.data.dao.ColorBreakdownRow
import com.closet.core.data.model.WashStatus

// ─── Wash status ──────────────────────────────────────────────────────────────

/** Clean vs Dirty item counts displayed as two side-by-side headline cards. */
@Composable
internal fun WashStatusSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    val clean = rows.firstOrNull { it.label == WashStatus.Clean.label }?.count ?: 0
    val dirty = rows.firstOrNull { it.label == WashStatus.Dirty.label }?.count ?: 0
    SectionHeader(stringResource(R.string.stats_section_wash_status))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatHeadlineCard(
            value = clean.toString(),
            label = stringResource(R.string.stats_wash_clean),
            accessibilityLabel = stringResource(R.string.stats_cd_wash_clean, clean),
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = dirty.toString(),
            label = stringResource(R.string.stats_wash_dirty),
            accessibilityLabel = stringResource(R.string.stats_cd_wash_dirty, dirty),
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── Category + subcategory combined ─────────────────────────────────────────

/**
 * Fixed hue values (HSV degrees) for the per-category color palette.
 * Each category is assigned a slot by its position in the sorted category list.
 */
private val categoryBaseHues = listOf(210f, 20f, 100f, 270f, 345f, 45f, 175f, 290f)

/**
 * Generates [count] colors for a single category's subcategory segments.
 * Saturation steps from vivid (0.85) down to pastel (0.30) so the largest subcategory
 * (first segment) is most saturated and smaller ones fade toward pastel.
 */
private fun subcategoryColors(categoryIndex: Int, count: Int): List<Color> {
    val hue = categoryBaseHues[categoryIndex % categoryBaseHues.size]
    return (0 until count).map { i ->
        val saturation = if (count == 1) 0.65f
        else 0.85f - (i.toFloat() / (count - 1)) * 0.55f
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.90f)))
    }
}

/**
 * Combined category + subcategory section. Renders one [SegmentedBar] per category where
 * each segment represents a subcategory (or the category itself for items with no subcategory).
 * Categories are ordered by total item count descending; segments within each bar are ordered
 * by count descending. Returns early if [rows] is empty.
 */
@Composable
internal fun CategorySubcategorySection(
    rows: List<CategorySubcategoryRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val otherColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val sortedCategories = remember(rows) {
        rows.groupBy { it.categoryLabel }
            .entries
            .sortedByDescending { (_, subcats) -> subcats.sumOf { it.count } }
    }
    SectionHeader(stringResource(R.string.stats_section_category_count))
    sortedCategories.forEachIndexed { catIndex, (categoryLabel, subcategoryRows) ->
        val colors = subcategoryColors(catIndex, subcategoryRows.size)
        val segments = subcategoryRows.mapIndexed { i, row ->
            BarSegment(label = row.subcategoryLabel, count = row.count, color = colors[i])
        }
        val (visible, hidden) = remember(segments, otherColor) {
            segments.withOtherGroup(otherColor = otherColor)
        }
        Column(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            SegmentedBar(
                segments = visible,
                hiddenSegments = hidden,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            subcategoryRows.chunked(2).forEachIndexed { chunkIdx, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pair.forEachIndexed { pairIdx, row ->
                        SubcategoryLegendRow(
                            label = row.subcategoryLabel,
                            count = row.count,
                            color = colors.getOrElse(chunkIdx * 2 + pairIdx) { Color.Gray },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SubcategoryLegendRow(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Item count per occasion. */
@Composable
internal fun OccasionBreakdownSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    BreakdownSection(
        title = stringResource(R.string.stats_section_occasion),
        rows = rows,
        modifier = modifier
    )
}

/**
 * Shared "label + count + progress bar" layout used by category count, subcategory,
 * and occasion breakdown sections. Returns early (renders nothing) if [rows] is empty.
 */
@Composable
internal fun BreakdownSection(
    title: String,
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val maxCount = rows.maxOf { it.count }.takeIf { it > 0 } ?: 1
    SectionHeader(title)
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            BreakdownBar(row = row, maxCount = maxCount)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Single label + count + proportional [LinearProgressIndicator] row inside [BreakdownSection]. */
@Composable
private fun BreakdownBar(row: BreakdownRow, maxCount: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = row.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { row.count.toFloat() / maxCount },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// ─── Color breakdown ──────────────────────────────────────────────────────────

/**
 * Color breakdown section: a [SegmentedBar] at the top where each segment is filled with the
 * actual item color, followed by a per-row legend showing the swatch, label, and count.
 */
@Composable
internal fun ColorBreakdownSection(
    rows: List<ColorBreakdownRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val otherColor = MaterialTheme.colorScheme.outlineVariant
    val segments = remember(rows) {
        rows.map { row ->
            BarSegment(
                label = row.label,
                count = row.count,
                color = runCatching { Color(android.graphics.Color.parseColor(row.hex)) }
                    .getOrElse { Color.Gray }
            )
        }
    }
    val (visible, hidden) = remember(segments, otherColor) {
        segments.withOtherGroup(otherColor = otherColor)
    }
    SectionHeader(stringResource(R.string.stats_section_color))
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SegmentedBar(
            segments = visible,
            hiddenSegments = hidden,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pair.forEach { row -> ColorLegendRow(row = row, modifier = Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Single legend row inside [ColorBreakdownSection]: a 12 dp color swatch circle, the color
 * label, and item count. Falls back to [Color.Gray] when [ColorBreakdownRow.hex] is null
 * or unparseable.
 */
@Composable
private fun ColorLegendRow(row: ColorBreakdownRow, modifier: Modifier = Modifier) {
    val swatchColor = remember(row.hex) {
        runCatching { Color(android.graphics.Color.parseColor(row.hex)) }
            .getOrElse { Color.Gray }
    }
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
            )
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = row.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
