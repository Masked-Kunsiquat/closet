package com.closet.features.outfits

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/**
 * Top-level route for the Journal / wear-history screen.
 *
 * [initialDate] — when non-null, the journal jumps to that date and opens the day detail sheet.
 * Used for deep-linking from the Item Wear History section of [ClothingDetailScreen].
 */
@Serializable
data class JournalRoute(val initialDate: String? = null)

/** Registers the [JournalRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.journalScreen() {
    composable<JournalRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<JournalRoute>()
        JournalScreen(initialDate = route.initialDate)
    }
}
