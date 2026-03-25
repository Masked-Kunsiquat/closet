package com.closet.features.recommendations

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Recommendations screen. */
@Serializable
object RecommendationRoute

/** Navigates to the Recommendations screen, applying [navOptions] for back-stack management. */
fun NavController.navigateToRecommendations(navOptions: NavOptions? = null) {
    navigate(RecommendationRoute, navOptions)
}

/**
 * Registers the [RecommendationRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onNavigateUp    Called when the user presses back from the Recommendations screen.
 * @param onNavigateToLog Called with item IDs when the user taps "Log it" on a combo.
 *                        Pass null (the default) to disable the "Log it" button until
 *                        [OutfitBuilderDestination] supports pre-selected item IDs.
 */
fun NavGraphBuilder.recommendationScreen(
    onNavigateUp: () -> Unit = {},
    onNavigateToLog: ((List<Long>) -> Unit)? = null,
) {
    composable<RecommendationRoute> {
        RecommendationScreen(
            onNavigateUp = onNavigateUp,
            onNavigateToLog = onNavigateToLog,
        )
    }
}
