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

@Composable
fun ClosetTheme(
    accent: ClosetAccent = ClosetAccent.Amber,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
