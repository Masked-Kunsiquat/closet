package com.closet.shared

import com.closet.features.outfits.OutfitBuilderDestination
import com.closet.features.wardrobe.AddClothingDestination
import com.closet.features.wardrobe.BulkWashDestination
import com.closet.features.wardrobe.ClosetDestination

/** A launcher/app shortcut request, platform-neutral. Each platform maps its own trigger to one of these. */
sealed interface AppShortcut {
    data object QuickAdd : AppShortcut
    data object LogFit : AppShortcut
    data object LaundryDay : AppShortcut
    data class Category(val categoryId: Long?) : AppShortcut
}

/**
 * One delivery of an [AppShortcut]. Compared by identity (not a data class), so the same
 * shortcut delivered twice is two requests: a repeat that arrives before the previous one is
 * observed as consumed still re-runs the routing effect.
 */
class ShortcutRequest(val shortcut: AppShortcut)

/** The navigation destination a shortcut opens. */
internal fun AppShortcut.destination(): Any = when (this) {
    AppShortcut.QuickAdd -> AddClothingDestination(openCamera = true)
    AppShortcut.LogFit -> OutfitBuilderDestination()
    AppShortcut.LaundryDay -> BulkWashDestination
    is AppShortcut.Category -> ClosetDestination(initialCategoryId = categoryId)
}
