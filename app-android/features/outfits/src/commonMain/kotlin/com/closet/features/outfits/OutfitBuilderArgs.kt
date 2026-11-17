package com.closet.features.outfits

import androidx.lifecycle.SavedStateHandle

/** The `preselectedItemIds` navigation argument, or an empty list when absent. */
internal expect fun SavedStateHandle.preselectedItemIds(): List<Long>
