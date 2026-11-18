package com.closet.features.wardrobe

import androidx.lifecycle.SavedStateHandle

internal actual fun SavedStateHandle.closetInitialCategoryId(): Long? = get<Long>("initialCategoryId")

internal actual fun SavedStateHandle.detailItemId(): Long =
    requireNotNull(get<Long>("itemId")) { "ClothingDetailDestination requires itemId" }

internal actual fun SavedStateHandle.editItemId(): Long? = get<Long>("itemId")

internal actual fun SavedStateHandle.addOpenCamera(): Boolean = get<Boolean>("openCamera") ?: false
