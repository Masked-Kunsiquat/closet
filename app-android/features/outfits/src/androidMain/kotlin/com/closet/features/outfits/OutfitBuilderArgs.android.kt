package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute

internal actual fun SavedStateHandle.preselectedItemIds(): List<Long> =
    toRoute<OutfitBuilderDestination>().preselectedItemIds
