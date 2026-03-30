package com.closet.shortcuts

/**
 * Intent action strings and extras used by all App Shortcuts.
 *
 * These constants are shared between:
 * - `shortcuts.xml` (static shortcut intent declarations)
 * - [com.closet.MainActivity] (intent dispatch on cold-start and re-launch)
 * - [com.closet.navigation.ClosetNavGraph] (destination routing)
 * - [com.closet.features.wardrobe.ClosetViewModel] (pinned category shortcut builder)
 */
object ShortcutActions {

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
}
