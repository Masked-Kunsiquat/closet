package com.closet.features.outfits

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
object OutfitsRoute

fun NavController.navigateToOutfits(navOptions: NavOptions? = null) {
    this.navigate(OutfitsRoute, navOptions)
}

fun NavGraphBuilder.outfitsScreen() {
    composable<OutfitsRoute> {
        // TODO: Implement OutfitsScreen
    }
}
