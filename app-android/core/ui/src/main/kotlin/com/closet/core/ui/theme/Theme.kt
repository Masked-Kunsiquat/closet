package com.closet.core.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Root Material 3 theme for the app.
 *
 * Color scheme selection priority:
 * 1. Dynamic color (Material You) on Android 12+ when [dynamicColor] is `true`.
 * 2. Accent-based MTB-generated [ColorScheme], with the correct contrast variant
 *    selected automatically from [UiModeManager.contrast] on API 34+.
 *
 * Status bar and navigation bar icon appearance is updated to match the current
 * light/dark mode via [WindowCompat].
 *
 * @param accent The accent palette to apply when dynamic color is unavailable.
 * @param darkTheme Whether to use dark colors; defaults to the system setting.
 * @param dynamicColor Whether to use Material You wallpaper-derived colors on Android 12+.
 *   Defaults to `false` so the user-selected [accent] always applies unless opted in via Settings.
 * @param content The composable content to theme.
 */
@Composable
fun ClosetTheme(
    accent: ClosetAccent = ClosetAccent.Amber,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val uiModeManager = LocalContext.current.getSystemService(UiModeManager::class.java)
        uiModeManager?.contrast ?: 0f
    } else {
        0f
    }

    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> accentColorScheme(accent, darkTheme, contrastLevel)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * Returns the pre-baked MTB [ColorScheme] for [accent] that best matches
 * [darkTheme] and the system's [contrastLevel].
 *
 * Contrast thresholds mirror [UiModeManager.contrast] semantics:
 * - `>= 1.0f` → high contrast
 * - `>= 0.5f` → medium contrast
 * - otherwise → standard
 */
private fun accentColorScheme(
    accent: ClosetAccent,
    darkTheme: Boolean,
    contrastLevel: Float,
): ColorScheme = when (accent) {
    ClosetAccent.Amber -> when {
        darkTheme && contrastLevel >= 1f -> amberHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> amberMediumContrastDarkScheme
        darkTheme -> amberDarkScheme
        contrastLevel >= 1f -> amberHighContrastLightScheme
        contrastLevel >= 0.5f -> amberMediumContrastLightScheme
        else -> amberLightScheme
    }
    ClosetAccent.Coral -> when {
        darkTheme && contrastLevel >= 1f -> coralHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> coralMediumContrastDarkScheme
        darkTheme -> coralDarkScheme
        contrastLevel >= 1f -> coralHighContrastLightScheme
        contrastLevel >= 0.5f -> coralMediumContrastLightScheme
        else -> coralLightScheme
    }
    ClosetAccent.Sage -> when {
        darkTheme && contrastLevel >= 1f -> sageHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> sageMediumContrastDarkScheme
        darkTheme -> sageDarkScheme
        contrastLevel >= 1f -> sageHighContrastLightScheme
        contrastLevel >= 0.5f -> sageMediumContrastLightScheme
        else -> sageLightScheme
    }
    ClosetAccent.Sky -> when {
        darkTheme && contrastLevel >= 1f -> skyHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> skyMediumContrastDarkScheme
        darkTheme -> skyDarkScheme
        contrastLevel >= 1f -> skyHighContrastLightScheme
        contrastLevel >= 0.5f -> skyMediumContrastLightScheme
        else -> skyLightScheme
    }
    ClosetAccent.Lavender -> when {
        darkTheme && contrastLevel >= 1f -> lavenderHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> lavenderMediumContrastDarkScheme
        darkTheme -> lavenderDarkScheme
        contrastLevel >= 1f -> lavenderHighContrastLightScheme
        contrastLevel >= 0.5f -> lavenderMediumContrastLightScheme
        else -> lavenderLightScheme
    }
    ClosetAccent.Rose -> when {
        darkTheme && contrastLevel >= 1f -> roseHighContrastDarkScheme
        darkTheme && contrastLevel >= 0.5f -> roseMediumContrastDarkScheme
        darkTheme -> roseDarkScheme
        contrastLevel >= 1f -> roseHighContrastLightScheme
        contrastLevel >= 0.5f -> roseMediumContrastLightScheme
        else -> roseLightScheme
    }
}
