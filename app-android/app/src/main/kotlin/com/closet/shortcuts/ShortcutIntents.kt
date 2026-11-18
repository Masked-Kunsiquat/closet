package com.closet.shortcuts

import android.content.Intent
import com.closet.shared.AppShortcut

/** Maps a launcher-shortcut [Intent] to the platform-neutral [AppShortcut], or null if it isn't one. */
fun Intent.toAppShortcut(): AppShortcut? = when (action) {
    ShortcutActions.ACTION_QUICK_ADD -> AppShortcut.QuickAdd
    ShortcutActions.ACTION_LOG_FIT -> AppShortcut.LogFit
    ShortcutActions.ACTION_LAUNDRY_DAY -> AppShortcut.LaundryDay
    ShortcutActions.ACTION_CATEGORY -> AppShortcut.Category(
        getLongExtra(ShortcutActions.EXTRA_CATEGORY_ID, -1L).takeIf { it != -1L },
    )
    else -> null
}
