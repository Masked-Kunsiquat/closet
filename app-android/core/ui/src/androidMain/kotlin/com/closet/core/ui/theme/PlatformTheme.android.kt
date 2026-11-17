package com.closet.core.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material You scheme on Android 12+ (API 31); `null` below that. */
@Composable
internal actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

/** Updates status bar and navigation bar icon appearance to match [darkTheme] via [WindowCompat]. */
@Composable
internal actual fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/**
 * Returns the current system contrast level as reactive Compose state.
 *
 * On Android 14+ (API 34) this registers a [UiModeManager.ContrastChangeListener] so
 * the returned value updates — and triggers recomposition — whenever the user changes
 * the system contrast setting while the app is running. On older APIs it always returns
 * `0f` (standard contrast).
 */
@Composable
internal actual fun rememberContrastLevel(): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0f
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    val contrastState = remember { mutableStateOf(uiModeManager?.contrast ?: 0f) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { newContrast ->
            contrastState.value = newContrast
        }
        uiModeManager?.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModeManager?.removeContrastChangeListener(listener) }
    }
    return contrastState.value
}

actual val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
