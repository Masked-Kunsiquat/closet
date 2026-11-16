package com.closet.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The platform's dynamic (wallpaper-derived) color scheme, or `null` when the platform has none.
 * Android returns Material You on API 31+; JVM and iOS always return `null`.
 */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** The platform contrast level: `0f` standard, `>= 0.5f` medium, `>= 1f` high. */
@Composable
internal expect fun rememberContrastLevel(): Float

/** Aligns the system bars' icon appearance with [darkTheme]. A no-op where there are no system bars. */
@Composable
internal expect fun SystemBarAppearance(darkTheme: Boolean)
