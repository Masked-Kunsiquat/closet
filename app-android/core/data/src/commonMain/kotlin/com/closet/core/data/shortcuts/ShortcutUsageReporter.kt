package com.closet.core.data.shortcuts

/** Tells the platform a launcher shortcut was used, so it can rank shortcuts by use. */
fun interface ShortcutUsageReporter {
    fun reportUsed(shortcutId: String)
}

/** A [ShortcutUsageReporter] for platforms without launcher shortcuts (JVM, iOS). */
object NoOpShortcutUsageReporter : ShortcutUsageReporter {
    override fun reportUsed(shortcutId: String) = Unit
}
