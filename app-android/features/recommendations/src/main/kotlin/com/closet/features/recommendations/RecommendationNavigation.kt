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
 *                        Navigate to the outfit logging flow with those IDs pre-loaded.
 *                        Pass a no-op if the log screen is not yet wired.
 */
fun NavGraphBuilder.recommendationScreen(
    onNavigateUp: () -> Unit = {},
    onNavigateToLog: (List<Long>) -> Unit = {},
) {
    composable<RecommendationRoute> {
        RecommendationScreen(
            onNavigateUp = onNavigateUp,
            onNavigateToLog = onNavigateToLog,
        )
    }
}
