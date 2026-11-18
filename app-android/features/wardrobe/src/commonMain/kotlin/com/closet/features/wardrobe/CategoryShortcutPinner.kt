package com.closet.features.wardrobe

/**
 * Asks the launcher to pin a shortcut that opens the Closet filtered to a category.
 * No-op where pinned shortcuts aren't supported.
 */
fun interface CategoryShortcutPinner {
    fun pin(categoryId: Long, label: String)
}
