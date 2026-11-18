package com.closet.features.wardrobe

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Pins a launcher shortcut for a category that deep-links back to the Closet screen
 * pre-filtered to that category.
 * No-op on launchers that don't support pinned shortcuts.
 * Note: pinned shortcuts do not count against the static + dynamic shortcut slot budget.
 */
class AndroidCategoryShortcutPinner(private val appContext: Context) : CategoryShortcutPinner {
    override fun pin(categoryId: Long, label: String) {
        val shortcutInfo = ShortcutInfoCompat.Builder(appContext, "category_$categoryId")
            .setShortLabel(label.take(10))
            .setLongLabel(label.take(25))
            .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_shortcut_category))
            .setIntent(
                Intent().apply {
                    action = "com.closet.shortcut.CATEGORY"
                    setClassName(appContext.packageName, "com.closet.MainActivity")
                    putExtra("com.closet.shortcut.extra.CATEGORY_ID", categoryId)
                }
            )
            .build()
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
            ShortcutManagerCompat.requestPinShortcut(appContext, shortcutInfo, null)
        }
    }
}
