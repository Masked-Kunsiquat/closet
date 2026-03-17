package com.closet.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Generates a Material 3 ColorScheme based on a ClosetAccent.
 */
private fun getDarkColorScheme(accent: ClosetAccent) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Surface0,
    primaryContainer = accent.muted,
    onPrimaryContainer = TextPrimary,
    secondary = accent.muted,
    onSecondary = TextPrimary,
    background = Surface0,
    surface = Surface1,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = ErrorColor
)

private fun getLightColorScheme(accent: ClosetAccent) = lightColorScheme(
    primary = accent.primary,
    onPrimary = White,
    primaryContainer = accent.primary.copy(alpha = 0.1f),
    onPrimaryContainer = accent.muted,
    secondary = accent.muted,
    onSecondary = White,
    background = White,
    surface = White,
    onBackground = Surface0,
    onSurface = Surface0,
    surfaceVariant = Gray100, // From previous Color.kt
    onSurfaceVariant = TextSecondary,
    outline = BorderMuted,
    error = ErrorColor
)

@Composable
fun ClosetTheme(
    accent: ClosetAccent = ClosetAccent.Amber, // Default from tokens.ts
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> getDarkColorScheme(accent)
        else -> getLightColorScheme(accent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
