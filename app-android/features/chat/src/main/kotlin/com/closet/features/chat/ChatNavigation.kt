package com.closet.features.chat

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Chat (Wardrobe Assistant) screen. */
@Serializable
object ChatRoute

/** Navigates to the Chat screen, applying [navOptions] for back-stack management. */
fun NavController.navigateToChat(navOptions: NavOptions? = null) {
    navigate(ChatRoute, navOptions)
}

/**
 * Registers the [ChatRoute] composable destination in the [NavGraphBuilder].
 *
 * @param onNavigateToItem          Called with an item ID when the user taps an item chip in
 *                                  a [WithItems] or [WithOutfit] message.
 * @param onNavigateToRecommendations Called when the user taps "Alternatives →" on an outfit
 *                                  mini-card — deeplinks to the Recommendations screen.
 * @param onNavigateToLog           Called with item IDs when the user taps "Log it" on an outfit
 *                                  mini-card. Pass null (the default) to disable the button.
 */
fun NavGraphBuilder.chatScreen(
    onNavigateToItem: (Long) -> Unit = {},
    onNavigateToRecommendations: () -> Unit = {},
    onNavigateToLog: ((List<Long>) -> Unit)? = null,
) {
    composable<ChatRoute> {
        ChatScreen(
            onNavigateToItem = onNavigateToItem,
            onNavigateToRecommendations = onNavigateToRecommendations,
            onNavigateToLog = onNavigateToLog,
        )
    }
}
