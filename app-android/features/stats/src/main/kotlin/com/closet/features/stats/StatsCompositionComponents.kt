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
import androidx.compose.ui.unit.dp
import com.closet.core.data.dao.BreakdownRow
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

// ─── Category / subcategory / occasion ───────────────────────────────────────

/** Item count per category, shown as labelled progress bars. */
@Composable
internal fun CategoryCountSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    BreakdownSection(
        title = stringResource(R.string.stats_section_category_count),
        rows = rows,
        modifier = modifier
    )
}

/** Item count per subcategory. Hidden automatically when no items have a subcategory assigned. */
@Composable
internal fun SubcategoryBreakdownSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    BreakdownSection(
        title = stringResource(R.string.stats_section_subcategory),
        rows = rows,
        modifier = modifier
    )
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

/** Item count per color with a color swatch rendered next to each label. */
@Composable
internal fun ColorBreakdownSection(
    rows: List<ColorBreakdownRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val maxCount = rows.maxOf { it.count }.takeIf { it > 0 } ?: 1
    SectionHeader(stringResource(R.string.stats_section_color))
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            ColorBreakdownBar(row = row, maxCount = maxCount)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Single row inside [ColorBreakdownSection]: a 12 dp color swatch circle, the color label,
 * item count, and a proportional [LinearProgressIndicator]. Falls back to the theme primary
 * color when [ColorBreakdownRow.hex] is null or unparseable.
 */
@Composable
private fun ColorBreakdownBar(row: ColorBreakdownRow, maxCount: Int) {
    val swatchColor = remember(row.hex) {
        runCatching { Color(android.graphics.Color.parseColor(row.hex)) }
            .getOrNull()
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(swatchColor ?: MaterialTheme.colorScheme.primary)
                )
                Text(text = row.label, style = MaterialTheme.typography.bodyMedium)
            }
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
