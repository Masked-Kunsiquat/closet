package com.closet.core.data.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat

/** Reports shortcut usage to the Android launcher via [ShortcutManagerCompat]. */
class AndroidShortcutUsageReporter(private val context: Context) : ShortcutUsageReporter {
    override fun reportUsed(shortcutId: String) {
        ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
    }
}
