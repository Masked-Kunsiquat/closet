package com.closet.features.stats

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.data.dao.BreakdownRow
import com.closet.core.data.dao.CategorySubcategoryRow
import com.closet.core.data.dao.ColorBreakdownRow
import com.closet.core.data.dao.CostPerWearItem
import com.closet.core.data.dao.StatItem
import com.closet.core.data.dao.StatsOverview
import com.closet.core.ui.theme.ClosetTheme

// ─── Shared preview data ──────────────────────────────────────────────────────

private val previewOverview = StatsOverview(
    totalItems = 24,
    wornItems = 18,
    neverWornItems = 6,
    totalValue = 1250.99
)

private val previewCategorySubcategory = listOf(
    CategorySubcategoryRow("Tops", "T-Shirts", 8),
    CategorySubcategoryRow("Tops", "Blouses", 3),
    CategorySubcategoryRow("Tops", "Tops", 2),
    CategorySubcategoryRow("Bottoms", "Jeans", 5),
    CategorySubcategoryRow("Bottoms", "Trousers", 2),
    CategorySubcategoryRow("Outerwear", "Outerwear", 4),
)

private val previewColorBreakdown = listOf(
    ColorBreakdownRow("Black", "#1C1C1C", 8),
    ColorBreakdownRow("White", "#F5F5F5", 6),
    ColorBreakdownRow("Navy", "#1B2A4A", 4),
    ColorBreakdownRow("Olive", "#6B7C3A", 3),
    ColorBreakdownRow("Cream", "#F5F0E8", 3),
)

private val previewMostWorn = listOf(
    StatItem(1L, "Vintage Denim Jacket", null, 12),
    StatItem(2L, "White Linen Shirt", null, 9),
    StatItem(3L, "Black Chinos", null, 7),
)

private val previewCostPerWear = listOf(
    CostPerWearItem(1L, "Vintage Denim Jacket", null, 85.00, 12, 7.08),
    CostPerWearItem(2L, "White Linen Shirt", null, 45.00, 9, 5.00),
)

private val previewCategoryWear = listOf(
    BreakdownRow("Tops", 28),
    BreakdownRow("Bottoms", 21),
    BreakdownRow("Outerwear", 9),
)

private val previewWashStatus = listOf(
    BreakdownRow("Clean", 18),
    BreakdownRow("Dirty", 6),
)

private val previewOccasions = listOf(
    BreakdownRow("Casual", 14),
    BreakdownRow("Work", 7),
    BreakdownRow("Evening", 3),
)

private val previewNeverWorn = listOf(
    StatItem(10L, "Silk Evening Dress", null, 0),
    StatItem(11L, "Wool Blazer", null, 0),
)

private val previewUiState = StatsUiState(
    overview = previewOverview,
    mostWorn = previewMostWorn,
    costPerWear = previewCostPerWear,
    categorySubcategoryBreakdown = previewCategorySubcategory,
    categoryWear = previewCategoryWear,
    totalLogsCount = 58,
    neverWorn = previewNeverWorn,
    selectedPeriod = StatPeriod.ALL_TIME,
    colorBreakdown = previewColorBreakdown,
    occasionBreakdown = previewOccasions,
    washStatus = previewWashStatus,
)

// ─── Full screen ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Stats - Populated - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Stats - Populated - Dark")
@Composable
private fun StatsContentPopulatedPreview() {
    ClosetTheme {
        StatsContent(
            uiState = previewUiState,
            resolveImagePath = { null },
            onSelectPeriod = {},
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Stats - Empty Wardrobe")
@Composable
private fun StatsContentEmptyPreview() {
    ClosetTheme {
        StatsContent(
            uiState = StatsUiState(),
            resolveImagePath = { null },
            onSelectPeriod = {},
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Stats - Items But No Logs")
@Composable
private fun StatsContentNoLogsPreview() {
    ClosetTheme {
        StatsContent(
            uiState = StatsUiState(
                overview = previewOverview,
                categorySubcategoryBreakdown = previewCategorySubcategory,
                colorBreakdown = previewColorBreakdown,
                washStatus = previewWashStatus,
                occasionBreakdown = previewOccasions,
                neverWorn = previewNeverWorn,
                totalLogsCount = 0,
            ),
            resolveImagePath = { null },
            onSelectPeriod = {},
            onItemClick = {}
        )
    }
}

// ─── Headline cards ───────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Headline Cards - With Value")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Headline Cards - Dark")
@Composable
private fun HeadlineCardsPreview() {
    ClosetTheme {
        Surface {
            HeadlineCardsRow(overview = previewOverview)
        }
    }
}

@Preview(showBackground = true, name = "Headline Cards - No Value")
@Composable
private fun HeadlineCardsNoValuePreview() {
    ClosetTheme {
        Surface {
            HeadlineCardsRow(
                overview = StatsOverview(totalItems = 10, wornItems = 4, neverWornItems = 6, totalValue = null)
            )
        }
    }
}

// ─── Category + subcategory ───────────────────────────────────────────────────

@Preview(showBackground = true, name = "Category Subcategory - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Category Subcategory - Dark")
@Composable
private fun CategorySubcategorySectionPreview() {
    ClosetTheme {
        Surface {
            CategorySubcategorySection(rows = previewCategorySubcategory)
        }
    }
}

@Preview(showBackground = true, name = "Category Subcategory - Single Category No Subcats")
@Composable
private fun CategorySubcategoryFlatPreview() {
    ClosetTheme {
        Surface {
            CategorySubcategorySection(
                rows = listOf(
                    CategorySubcategoryRow("Accessories", "Accessories", 7),
                )
            )
        }
    }
}

// ─── Color breakdown ──────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Color Breakdown - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Color Breakdown - Dark")
@Composable
private fun ColorBreakdownSectionPreview() {
    ClosetTheme {
        Surface {
            ColorBreakdownSection(rows = previewColorBreakdown)
        }
    }
}

// ─── Segmented bar ────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Segmented Bar - Multiple Segments")
@Composable
private fun SegmentedBarPreview() {
    ClosetTheme {
        Surface {
            val segments = listOf(
                BarSegment("T-Shirts", 8, androidx.compose.ui.graphics.Color(0xFF38BDF8)),
                BarSegment("Blouses", 3, androidx.compose.ui.graphics.Color(0xFF93C5FD)),
                BarSegment("Tops", 2, androidx.compose.ui.graphics.Color(0xFFBFDBFE)),
            )
            SegmentedBar(
                segments = segments,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// ─── Never worn ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Never Worn - With Items")
@Composable
private fun NeverWornSectionPreview() {
    ClosetTheme {
        Surface {
            NeverWornSection(
                items = previewNeverWorn,
                resolveImagePath = { null },
                onItemClick = {}
            )
        }
    }
}
