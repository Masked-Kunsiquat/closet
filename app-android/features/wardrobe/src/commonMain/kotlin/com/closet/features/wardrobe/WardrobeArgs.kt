package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle

/** [ClosetDestination.initialCategoryId], or null when absent. */
internal expect fun SavedStateHandle.closetInitialCategoryId(): Long?

/** [ClothingDetailDestination.itemId]; the route always carries it. */
internal expect fun SavedStateHandle.detailItemId(): Long

/** [EditClothingDestination.itemId], or null on the add route. */
internal expect fun SavedStateHandle.editItemId(): Long?

/** [AddClothingDestination.openCamera], or false when absent. */
internal expect fun SavedStateHandle.addOpenCamera(): Boolean
