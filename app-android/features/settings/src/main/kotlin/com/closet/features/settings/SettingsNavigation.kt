package com.closet.features.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Settings screen. */
@Serializable
object SettingsRoute

/** Route for the AI & Image Tools sub-screen. */
@Serializable
object AiSettingsRoute

/**
 * Navigates to [SettingsRoute], optionally applying [navOptions] for back-stack
 * management (e.g. `singleTop`, `popUpTo`).
 */
fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

/** Navigates to the [AiSettingsRoute] sub-screen. */
fun NavController.navigateToAiSettings() {
    navigate(AiSettingsRoute)
}

/**
 * Registers the [SettingsRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onNavigateUp         Called when the user taps the back arrow.
 * @param onNavigateToBulkWash Called when the user taps the Laundry Day row.
 * @param onNavigateToAiSettings Called when the user taps the AI & Image Tools row.
 * @param onNavigateToBackup   Called when the user taps the Backup & Restore row.
 */
fun NavGraphBuilder.settingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToBulkWash: () -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateUp = onNavigateUp,
            onNavigateToBulkWash = onNavigateToBulkWash,
            onNavigateToAiSettings = onNavigateToAiSettings,
            onNavigateToBackup = onNavigateToBackup,
        )
    }
}

/**
 * Registers the [AiSettingsRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onNavigateUp Called when the user taps the back arrow.
 */
fun NavGraphBuilder.aiSettingsScreen(
    onNavigateUp: () -> Unit,
) {
    composable<AiSettingsRoute> {
        AiSettingsScreen(onNavigateUp = onNavigateUp)
    }
}
