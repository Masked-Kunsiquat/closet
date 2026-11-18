package com.closet.features.stats

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Stats screen. */
@Serializable
object StatsRoute

/** Navigates to the Stats screen, applying [navOptions] for back-stack management. */
fun NavController.navigateToStats(navOptions: NavOptions? = null) {
    navigate(StatsRoute, navOptions)
}

/**
 * Registers the [StatsRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onItemClick Called with an item ID when the user taps a clothing item (most worn,
 *   cost-per-wear, or never-worn lists). The app module wires this to the item detail route.
 */
fun NavGraphBuilder.statsScreen(onItemClick: (Long) -> Unit = {}) {
    composable<StatsRoute> {
        StatsScreen(onItemClick = onItemClick)
    }
}
