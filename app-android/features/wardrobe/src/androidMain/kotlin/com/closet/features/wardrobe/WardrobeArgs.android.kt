package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute

internal actual fun SavedStateHandle.closetInitialCategoryId(): Long? =
    try { toRoute<ClosetDestination>().initialCategoryId } catch (_: Exception) { null }

internal actual fun SavedStateHandle.detailItemId(): Long =
    toRoute<ClothingDetailDestination>().itemId

internal actual fun SavedStateHandle.editItemId(): Long? =
    try { toRoute<EditClothingDestination>().itemId } catch (_: Exception) { null }

internal actual fun SavedStateHandle.addOpenCamera(): Boolean =
    try { toRoute<AddClothingDestination>().openCamera } catch (_: Exception) { false }
