package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle

internal actual fun SavedStateHandle.preselectedItemIds(): List<Long> =
    get<List<Long>>("preselectedItemIds") ?: emptyList()
