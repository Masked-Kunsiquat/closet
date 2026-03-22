package com.closet.features.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.ColorBreakdownRow
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

    val cdItems = stringResource(R.string.stats_cd_total_items, overview.totalItems)
    val cdWorn = stringResource(R.string.stats_cd_worn_pct, overview.wornItems, overview.totalItems)
    val cdValue = if (overview.totalValue != null) {
        stringResource(R.string.stats_cd_total_value, formattedValue)
    } else {
        stringResource(R.string.stats_cd_total_value_unknown)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatHeadlineCard(
            value = overview.totalItems.toString(),
            label = stringResource(R.string.stats_headline_items),
            accessibilityLabel = cdItems,
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = wornPct,
            label = stringResource(R.string.stats_headline_worn),
            accessibilityLabel = cdWorn,
            modifier = Modifier.weight(1f)
        )
        StatHeadlineCard(
            value = formattedValue,
            label = stringResource(R.string.stats_headline_value),
            accessibilityLabel = cdValue,
            modifier = Modifier.weight(1f)
        )
    }
}

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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = resolveImagePath(item.imagePath),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
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
    if (rows.isEmpty()) return
    SectionHeader(stringResource(R.string.stats_section_wear_by_category))

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(rows) {
        modelProducer.runTransaction {
            columnSeries { series(rows.map { it.count }) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { _, x, _ ->
                    rows.getOrNull(x.toInt())?.label ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
    )
}

// ─── Wardrobe composition ─────────────────────────────────────────────────────

/** Item count per category. */
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

/** Clean vs Dirty item counts displayed as two side-by-side headline cards. */
@Composable
internal fun WashStatusSection(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier
) {
    val clean = rows.firstOrNull { it.label == "Clean" }?.count ?: 0
    val dirty = rows.firstOrNull { it.label == "Dirty" }?.count ?: 0
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

/** Item count per color with a color swatch next to each label. */
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

/**
 * Shared "label + count + progress bar" layout used by category count, subcategory,
 * and occasion breakdown sections.
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
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = resolveImagePath(item.imagePath),
                contentDescription = item.name,
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

/**
 * Full-column empty state shown when the wardrobe has no items at all.
 */
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

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}
