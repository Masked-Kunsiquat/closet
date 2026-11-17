package com.closet.features.settings

import androidx.compose.runtime.Composable

/** No runtime permission on JVM: weather falls back to cached coordinates, so enabling always succeeds. */
@Composable
internal actual fun rememberWeatherEnabler(
    onEnabled: () -> Unit,
    onDeniedCanAskAgain: () -> Unit,
    onDeniedPermanently: () -> Unit,
): () -> Unit = onEnabled

/** There is no per-app system settings page on JVM, so this does nothing. */
@Composable
internal actual fun rememberOpenAppSettings(): () -> Unit = {}

/** JVM has no screen-wake control, so this does nothing. */
@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
}
