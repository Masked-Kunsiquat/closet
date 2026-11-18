package com.closet.features.outfits

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

/** Registers the [JournalRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.journalScreen() {
    composable<JournalRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<JournalRoute>()
        JournalScreen(initialDate = route.initialDate)
    }
}
