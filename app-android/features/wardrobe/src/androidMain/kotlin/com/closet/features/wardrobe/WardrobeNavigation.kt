package com.closet.features.wardrobe

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Registers [BulkWashDestination] → [BulkWashScreen] in the nav graph. */
fun NavGraphBuilder.bulkWashScreen(onBack: () -> Unit) {
    composable<BulkWashDestination> {
        BulkWashScreen(onBack = onBack)
    }
}
