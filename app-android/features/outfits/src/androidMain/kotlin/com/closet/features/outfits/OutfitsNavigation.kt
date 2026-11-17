package com.closet.features.outfits

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.koin.compose.viewmodel.koinViewModel

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
        val builderViewModel: OutfitBuilderViewModel? = parentEntry?.let {
            koinViewModel<OutfitBuilderViewModel>(viewModelStoreOwner = it)
        }
        WardrobePickerScreen(
            onBack = { navController.popBackStack() },
            onConfirm = { selectedIds ->
                builderViewModel?.addItems(selectedIds)
                navController.popBackStack()
            }
        )
    }
}
