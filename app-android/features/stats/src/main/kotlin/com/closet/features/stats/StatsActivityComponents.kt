package com.closet.features.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CostPerWearItem
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsOverview
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import java.io.File
import java.text.NumberFormat

// ─── Period selector ──────────────────────────────────────────────────────────

/**
 * Horizontally scrollable row of [FilterChip]s, one per [StatPeriod].
 * The chip matching [selectedPeriod] renders in the selected state.
 */
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

/** Localized display string for the period chip label. */
private val StatPeriod.label: String
    @Composable get() = when (this) {
        StatPeriod.ALL_TIME -> stringResource(R.string.stats_period_all_time)
        StatPeriod.LAST_30 -> stringResource(R.string.stats_period_30_days)
        StatPeriod.LAST_90 -> stringResource(R.string.stats_period_90_days)
        StatPeriod.THIS_YEAR -> stringResource(R.string.stats_period_this_year)
    }

// ─── Headline cards ───────────────────────────────────────────────────────────

/**
 * Three side-by-side [StatHeadlineCard]s showing total items, worn percentage, and total wardrobe
 * value. All three values are derived from [overview] and formatted for display.
 */
@Composable
internal fun HeadlineCardsRow(
    overview: StatsOverview,
    modifier: Modifier = Modifier
) {
    val wornPct = if (overview.totalItems > 0) {
        "${(overview.wornItems * 100.0 / overview.totalItems).roundToInt()}%"
    } else "0%"
    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val formattedValue = overview.totalValue?.let { currencyFormat.format(it) } ?: "—"

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

// ─── Most worn ────────────────────────────────────────────────────────────────

/**
 * Horizontally scrollable row of thumbnail cards for the most-worn items.
 * Each card shows the item's image with a wear-count badge overlaid in the bottom-right corner.
 * Tapping a card calls [onItemClick] with the item's ID.
 */
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

/**
 * Single 88 dp thumbnail card for a most-worn item.
 * Shows the item image with a wear-count badge overlaid in the bottom-right corner.
 */
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

/**
 * Vertical list of items ranked by cost-per-wear (cheapest per wear first).
 * Each row shows a thumbnail, item name, and the formatted cost-per-wear value.
 * Only items with a purchase price and at least one wear are included.
 */
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

/**
 * Single row in the cost-per-wear list: 48 dp thumbnail, item name, and the formatted
 * cost-per-wear value. Tapping the row navigates to the item detail screen.
 */
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
            text = stringResource(
                R.string.stats_cost_per_wear_formatted,
                NumberFormat.getCurrencyInstance().apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }.format(item.costPerWear)
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ─── Total logs callout ───────────────────────────────────────────────────────

/**
 * Elevated card displaying the total number of outfit logs for the selected period as a
 * centred, bold headline — e.g. "47 outfits logged". Uses a plural string resource so
 * the singular "1 outfit logged" is handled automatically.
 */
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

/** Vico animated column chart showing how many times each category has been worn. */
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
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, x, _ ->
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
