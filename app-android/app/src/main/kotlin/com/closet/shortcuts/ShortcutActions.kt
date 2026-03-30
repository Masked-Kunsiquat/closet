package com.closet.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat

/**
 * Intent action strings and extras used by all App Shortcuts.
 *
 * These constants are shared between:
 * - `shortcuts.xml` (static shortcut declarations)
 * - [com.closet.MainActivity] (intent dispatch on cold-start and re-launch)
 * - [com.closet.navigation.ClosetNavGraph] (destination routing)
 * - [com.closet.features.wardrobe.ClosetViewModel] (pinned category shortcut builder)
 *
 * **Slot budget**: launchers surface at most 4 shortcuts (static + dynamic combined).
 * [STATIC_SHORTCUT_COUNT] static shortcuts are declared in `shortcuts.xml`, leaving
 * [remainingDynamicSlots] free for dynamic shortcuts at runtime.
 * Pinned shortcuts (requested via [ShortcutManagerCompat.requestPinShortcut]) are
 * launcher-managed and do *not* count against this limit.
 */
object ShortcutActions {

    /** Number of static shortcuts declared in `shortcuts.xml`. */
    const val STATIC_SHORTCUT_COUNT = 3

    /** Shortcut IDs — must match the `android:shortcutId` values in `shortcuts.xml`. */
    const val ID_QUICK_ADD   = "quick_add"
    const val ID_LOG_FIT     = "log_fit"
    const val ID_LAUNDRY_DAY = "laundry_day"

    /** Intent actions set on each shortcut's `<intent>` element. */
    const val ACTION_QUICK_ADD   = "com.closet.shortcut.QUICK_ADD"
    const val ACTION_LOG_FIT     = "com.closet.shortcut.LOG_FIT"
    const val ACTION_LAUNDRY_DAY = "com.closet.shortcut.LAUNDRY_DAY"
    const val ACTION_CATEGORY    = "com.closet.shortcut.CATEGORY"

    /** Extra key for [ACTION_CATEGORY] — carries the target category's DB row ID. */
    const val EXTRA_CATEGORY_ID  = "com.closet.shortcut.extra.CATEGORY_ID"

    /**
     * Returns the number of dynamic shortcut slots that can still be filled.
     *
     * Call this before registering a new dynamic shortcut (not needed for pinned shortcuts).
     * If this returns 0, remove the least-recently-used dynamic shortcut first.
     *
     * Usage:
     * ```kotlin
     * if (ShortcutActions.remainingDynamicSlots(context) > 0) {
     *     ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcutInfo))
     * } else {
     *     // remove LRU dynamic shortcut, then add
     * }
     * ```
     */
    fun remainingDynamicSlots(context: Context): Int {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val usedDynamic = ShortcutManagerCompat.getDynamicShortcuts(context).size
        return maxOf(0, max - STATIC_SHORTCUT_COUNT - usedDynamic)
    }
}
