package com.closet.features.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Settings screen. */
@Serializable
object SettingsRoute

/**
 * Navigates to [SettingsRoute], optionally applying [navOptions] for back-stack
 * management (e.g. `singleTop`, `popUpTo`).
 */
fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

/**
 * Registers the [SettingsRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onNavigateUp Called when the user taps the back arrow.
 */
fun NavGraphBuilder.settingsScreen(onNavigateUp: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(onNavigateUp = onNavigateUp)
    }
}
