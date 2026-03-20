package com.closet.features.outfits

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// SavedStateHandle key used to pass selected item IDs back from WardrobePickerScreen.
const val PICKER_RESULT_KEY = "wardrobe_picker_item_ids"

@Serializable
object OutfitsRoute

@Serializable
object OutfitBuilderDestination

@Serializable
object WardrobePickerDestination

fun NavController.navigateToOutfits(navOptions: NavOptions? = null) {
    this.navigate(OutfitsRoute, navOptions)
}

fun NavController.navigateToOutfitBuilder() {
    this.navigate(OutfitBuilderDestination)
}

fun NavController.navigateToWardrobePicker() {
    this.navigate(WardrobePickerDestination)
}

fun NavGraphBuilder.outfitsScreen(navController: NavController) {
    composable<OutfitsRoute> {
        OutfitsScreen(
            onCreateOutfit = { navController.navigateToOutfitBuilder() }
        )
    }
}

fun NavGraphBuilder.outfitBuilderScreen(navController: NavController) {
    composable<OutfitBuilderDestination> {
        OutfitBuilderScreen(
            onBack = { navController.popBackStack() },
            onAddItems = { navController.navigateToWardrobePicker() }
        )
    }
}

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
