package com.closet.features.outfits

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Journal / wear-history screen. */
@Serializable
object JournalRoute

/** Registers the [JournalRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.journalScreen() {
    composable<JournalRoute> {
        // onDayClick will open DayDetailSheet in Phase 3
        JournalScreen()
    }
}
