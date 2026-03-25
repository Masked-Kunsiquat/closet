package com.closet.features.recommendations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Recommendations screen — placeholder until the ViewModel and UI are implemented
 * (see roadmap: "ViewModel" and "UI — suggestions screen").
 */
@Composable
fun RecommendationScreen(
    onNavigateUp: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Outfit recommendations coming soon")
    }
}
