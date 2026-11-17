package com.closet.features.settings

import androidx.compose.runtime.Composable

/** No runtime permission on iOS: weather falls back to cached coordinates, so enabling always succeeds. */
@Composable
internal actual fun rememberWeatherEnabler(
    onEnabled: () -> Unit,
    onDeniedCanAskAgain: () -> Unit,
    onDeniedPermanently: () -> Unit,
): () -> Unit = onEnabled

/** Not implemented on iOS yet (TODO: `UIApplicationOpenSettingsURLString`). */
@Composable
internal actual fun rememberOpenAppSettings(): () -> Unit = {}

/** Not implemented on iOS yet (TODO: `UIApplication.idleTimerDisabled`). */
@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
}
