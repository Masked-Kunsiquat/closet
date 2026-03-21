package com.closet.features.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stats screen — displays wardrobe analytics and wear history.
 * Placeholder: full layout implemented in Phase 4.
 *
 * @param onItemClick Called with an item ID when the user taps a clothing item
 *   (most worn, cost-per-wear, or never-worn lists).
 * @param viewModel Hilt-provided [StatsViewModel]; override in tests.
 */
@Composable
fun StatsScreen(
    onItemClick: (Long) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
