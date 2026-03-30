package com.closet.features.outfits

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Top-level route for the Outfits gallery screen. */
@Serializable
object OutfitsRoute

/**
 * Route for the Outfit Builder screen.
 *
 * [outfitId] = -1 means "create new"; any positive value opens edit mode for that outfit.
 */
@Serializable
data class OutfitBuilderDestination(val outfitId: Long = -1L)

/** Route for the Wardrobe Picker screen used to select items while building an outfit. */
@Serializable
object WardrobePickerDestination

/** Navigates to the Outfits gallery, applying [navOptions] for back-stack management. */
fun NavController.navigateToOutfits(navOptions: NavOptions? = null) {
    this.navigate(OutfitsRoute, navOptions)
}

/** Navigates to the Outfit Builder screen. Pass [outfitId] to open an existing outfit for editing. */
fun NavController.navigateToOutfitBuilder(outfitId: Long = -1L) {
    this.navigate(OutfitBuilderDestination(outfitId))
}

/** Navigates to the Wardrobe Picker screen from inside the Outfit Builder. */
fun NavController.navigateToWardrobePicker() {
    this.navigate(WardrobePickerDestination)
}

/** Registers the [OutfitsRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.outfitsScreen(
    navController: NavController,
    onGetSuggestions: () -> Unit = {},
) {
    composable<OutfitsRoute> {
        OutfitsScreen(
            onCreateOutfit = { navController.navigateToOutfitBuilder() },
            onEditOutfit = { outfitId -> navController.navigateToOutfitBuilder(outfitId) },
            onGetSuggestions = onGetSuggestions,
        )
    }
}

/** Registers the [OutfitBuilderDestination] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.outfitBuilderScreen(navController: NavController) {
    composable<OutfitBuilderDestination> {
        OutfitBuilderScreen(
            onBack = { navController.popBackStack() },
            onAddItems = { navController.navigateToWardrobePicker() }
        )
    }
}

/**
 * Registers the [WardrobePickerDestination] composable destination in the [NavGraphBuilder].
 *
 * On confirm, [addItems] is called directly on the [OutfitBuilderViewModel] scoped to the
 * previous back-stack entry, then the picker is popped from the stack.
 */
fun NavGraphBuilder.wardrobePickerScreen(navController: NavController) {
    composable<WardrobePickerDestination> {
        val parentEntry = navController.previousBackStackEntry
        val builderViewModel: OutfitBuilderViewModel? = parentEntry?.let { hiltViewModel(it) }
        WardrobePickerScreen(
            onBack = { navController.popBackStack() },
            onConfirm = { selectedIds ->
                builderViewModel?.addItems(selectedIds)
                navController.popBackStack()
            }
        )
    }
}
