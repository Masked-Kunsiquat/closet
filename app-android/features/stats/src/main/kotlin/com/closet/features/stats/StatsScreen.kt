package com.closet.features.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

/**
 * Stats screen — wardrobe analytics and wear history.
 *
 * Collects [StatsUiState] from [StatsViewModel] and delegates all rendering to
 * [StatsContent]. Navigation callbacks are passed in from the app module.
 *
 * @param onItemClick Called with an item ID when the user taps a clothing item.
 * @param viewModel Hilt-provided [StatsViewModel]; override in tests.
 */
@Composable
fun StatsScreen(
    onItemClick: (Long) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsContent(
        uiState = uiState,
        resolveImagePath = viewModel::resolveImagePath,
        onSelectPeriod = viewModel::selectPeriod,
        onItemClick = onItemClick
    )
}

/**
 * Stateless rendering of the Stats screen.
 *
 * Layout (single vertically scrollable column):
 *
 * Period selector
 * Headline cards (items · worn % · value)
 *
 * ── Activity (only when totalLogsCount > 0) ──────────────────────────────────
 *   Most Worn · Cost Per Wear · Total Logs Callout · Wear by Category (Vico)
 *
 * ── Wardrobe Composition (always when totalItems > 0) ────────────────────────
 *   Wash Status · Category Breakdown · Subcategory · Color · Occasion
 *
 * ── Never Worn (collapsible, always when totalItems > 0) ─────────────────────
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsContent(
    uiState: StatsUiState,
    resolveImagePath: (String?) -> File?,
    onSelectPeriod: (StatPeriod) -> Unit,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            PeriodSelectorRow(
                selectedPeriod = uiState.selectedPeriod,
                onSelectPeriod = onSelectPeriod
            )

            if (uiState.overview.totalItems == 0) {
                StatsEmptyState()
            } else {
                HeadlineCardsRow(overview = uiState.overview)

                // ── Activity ─────────────────────────────────────────────────
                if (uiState.totalLogsCount == 0) {
                    NoLogsInfoCard()
                } else {
                    if (uiState.mostWorn.isNotEmpty()) {
                        MostWornSection(
                            items = uiState.mostWorn,
                            resolveImagePath = resolveImagePath,
                            onItemClick = onItemClick
                        )
                    }
                    if (uiState.costPerWear.isNotEmpty()) {
                        CostPerWearSection(
                            items = uiState.costPerWear,
                            resolveImagePath = resolveImagePath,
                            onItemClick = onItemClick
                        )
                    }
                    TotalLogsCallout(count = uiState.totalLogsCount)
                    CategoryWearSection(rows = uiState.categoryWear)
                }

                // ── Wardrobe Composition ──────────────────────────────────────
                WashStatusSection(rows = uiState.washStatus)
                CategorySubcategorySection(rows = uiState.categorySubcategoryBreakdown)
                ColorBreakdownSection(rows = uiState.colorBreakdown)
                OccasionBreakdownSection(rows = uiState.occasionBreakdown)

                // ── Never Worn ────────────────────────────────────────────────
                NeverWornSection(
                    items = uiState.neverWorn,
                    resolveImagePath = resolveImagePath,
                    onItemClick = onItemClick
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
