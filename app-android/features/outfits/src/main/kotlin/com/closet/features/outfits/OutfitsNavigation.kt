package com.closet.features.outfits

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * [SavedStateHandle] key used to pass selected clothing item IDs back from
 * [WardrobePickerScreen] to [OutfitBuilderViewModel] via the previous back-stack entry.
 */
const val PICKER_RESULT_KEY = "wardrobe_picker_item_ids"

/** Top-level route for the Outfits gallery screen. */
@Serializable
object OutfitsRoute

/** Route for the Outfit Builder screen where users compose and save an outfit. */
@Serializable
object OutfitBuilderDestination

/** Route for the Wardrobe Picker screen used to select items while building an outfit. */
@Serializable
object WardrobePickerDestination

/** Navigates to the Outfits gallery, applying [navOptions] for back-stack management. */
fun NavController.navigateToOutfits(navOptions: NavOptions? = null) {
    this.navigate(OutfitsRoute, navOptions)
}

/** Navigates to the Outfit Builder screen. */
fun NavController.navigateToOutfitBuilder() {
    this.navigate(OutfitBuilderDestination)
}

/** Navigates to the Wardrobe Picker screen from inside the Outfit Builder. */
fun NavController.navigateToWardrobePicker() {
    this.navigate(WardrobePickerDestination)
}

/** Registers the [OutfitsRoute] composable destination in the [NavGraphBuilder]. */
fun NavGraphBuilder.outfitsScreen(navController: NavController) {
    composable<OutfitsRoute> {
        OutfitsScreen(
            onCreateOutfit = { navController.navigateToOutfitBuilder() }
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
 * On confirm, the selected item IDs are deposited into the previous back-stack entry's
 * [androidx.lifecycle.SavedStateHandle] under [PICKER_RESULT_KEY] before popping.
 */
fun NavGraphBuilder.wardrobePickerScreen(navController: NavController) {
    composable<WardrobePickerDestination> {
        WardrobePickerScreen(
            onBack = { navController.popBackStack() },
            onConfirm = { selectedIds ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(PICKER_RESULT_KEY, selectedIds.toLongArray())
                navController.popBackStack()
            }
        )
    }
}
