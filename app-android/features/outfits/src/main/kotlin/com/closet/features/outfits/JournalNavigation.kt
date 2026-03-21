package com.closet.features.outfits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Journal / wear-history screen. */
@Serializable
object JournalRoute

/** Registers the [JournalRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.journalScreen() {
    composable<JournalRoute> {
        JournalPlaceholderScreen()
    }
}

@Composable
private fun JournalPlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Journal — coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
