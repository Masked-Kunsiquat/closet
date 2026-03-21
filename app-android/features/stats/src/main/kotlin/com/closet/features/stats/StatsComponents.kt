package com.closet.features.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CostPerWearItem
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsOverview
import java.io.File
import java.text.NumberFormat

// ─── Period selector ──────────────────────────────────────────────────────────

@Composable
internal fun PeriodSelectorRow(
    selectedPeriod: StatPeriod,
    onSelectPeriod: (StatPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onSelectPeriod(period) },
                label = { Text(period.label) }
            )
        }
    }
}

private val StatPeriod.label: String
    @Composable get() = when (this) {
        StatPeriod.ALL_TIME -> stringResource(R.string.stats_period_all_time)
        StatPeriod.LAST_30 -> stringResource(R.string.stats_period_30_days)
        StatPeriod.LAST_90 -> stringResource(R.string.stats_period_90_days)
        StatPeriod.THIS_YEAR -> stringResource(R.string.stats_period_this_year)
    }

// ─── Headline cards ───────────────────────────────────────────────────────────

@Composable
internal fun HeadlineCardsRow(
    overview: StatsOverview,
    modifier: Modifier = Modifier
) {
    val wornPct = if (overview.totalItems > 0) {
        "${overview.wornItems * 100 / overview.totalItems}%"
    } else "0%"
    val formattedValue = overview.totalValue
        ?.let { NumberFormat.getCurrencyInstance().format(it) }
        ?: "—"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatHeadlineCard(
            value = overview.totalItems.toString(),
            label = stringResource(R.string.stats_headline_items),
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = wornPct,
            label = stringResource(R.string.stats_headline_worn),
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = formattedValue,
            label = stringResource(R.string.stats_headline_value),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatHeadlineCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Most worn ────────────────────────────────────────────────────────────────

@Composable
internal fun MostWornSection(
    items: List<StatItem>,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeader(stringResource(R.string.stats_section_most_worn))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(items, key = { it.id }) { item ->
            MostWornThumbnail(item, resolveImagePath, onItemClick)
        }
    }
}

@Composable
private fun MostWornThumbnail(
    item: StatItem,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onItemClick(item.id) }
    ) {
        AsyncImage(
            model = resolveImagePath(item.imagePath),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.stats_wear_badge,
                    item.wearCount,
                    item.wearCount
                ),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ─── Cost per wear ────────────────────────────────────────────────────────────

@Composable
internal fun CostPerWearSection(
    items: List<CostPerWearItem>,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit
) {
    SectionHeader(stringResource(R.string.stats_section_cost_per_wear))
    items.forEachIndexed { index, item ->
        CostPerWearRow(item, resolveImagePath, onItemClick)
        if (index < items.lastIndex) {
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp, end = 16.dp))
        }
    }
}

@Composable
private fun CostPerWearRow(
    item: CostPerWearItem,
    resolveImagePath: (String?) -> File?,
    onItemClick: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(item.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = resolveImagePath(item.imagePath),
            contentDescription = item.name,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${NumberFormat.getCurrencyInstance().format(item.costPerWear)} ${stringResource(R.string.stats_cost_per_wear_suffix)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ─── Total logs callout ───────────────────────────────────────────────────────

@Composable
internal fun TotalLogsCallout(
    count: Int,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pluralStringResource(R.plurals.stats_total_logs, count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Wear by category ─────────────────────────────────────────────────────────

@Composable
internal fun CategoryWearSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    val maxCount = rows.maxOfOrNull { it.count }?.takeIf { it > 0 } ?: 1
    SectionHeader(stringResource(R.string.stats_section_wear_by_category))
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            CategoryWearBar(row = row, maxCount = maxCount)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CategoryWearBar(row: BreakdownRow, maxCount: Int) {
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

// ─── Never worn ───────────────────────────────────────────────────────────────

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
            text = "${stringResource(R.string.stats_section_never_worn)} (${items.size})",
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
        AsyncImage(
            model = resolveImagePath(item.imagePath),
            contentDescription = item.name,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
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

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}
