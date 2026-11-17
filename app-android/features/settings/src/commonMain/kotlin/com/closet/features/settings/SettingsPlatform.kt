package com.closet.features.settings

import androidx.compose.runtime.Composable

/**
 * Returns an action that turns the weather feature on, asking for location permission first where
 * the platform requires it. Exactly one callback fires per request:
 * [onEnabled] when location is available, [onDeniedCanAskAgain] when it was denied but can be
 * asked for again, [onDeniedPermanently] when only system settings can grant it.
 */
@Composable
internal expect fun rememberWeatherEnabler(
    onEnabled: () -> Unit,
    onDeniedCanAskAgain: () -> Unit,
    onDeniedPermanently: () -> Unit,
): () -> Unit

/** Returns an action that opens this app's page in the system settings. */
@Composable
internal expect fun rememberOpenAppSettings(): () -> Unit

/** Keeps the screen awake while [enabled] is true, and releases the hold when leaving composition. */
@Composable
internal expect fun KeepScreenOn(enabled: Boolean)
