package com.closet.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asks for [Manifest.permission.ACCESS_COARSE_LOCATION] when it isn't held. A refusal calls
 * [onDeniedCanAskAgain] while Android would still show the rationale, else [onDeniedPermanently].
 */
@Composable
internal actual fun rememberWeatherEnabler(
    onEnabled: () -> Unit,
    onDeniedCanAskAgain: () -> Unit,
    onDeniedPermanently: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnEnabled by rememberUpdatedState(onEnabled)
    val currentOnDeniedCanAskAgain by rememberUpdatedState(onDeniedCanAskAgain)
    val currentOnDeniedPermanently by rememberUpdatedState(onDeniedPermanently)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            currentOnEnabled()
        } else {
            val shouldShow = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } ?: false
            if (shouldShow) currentOnDeniedCanAskAgain() else currentOnDeniedPermanently()
        }
    }

    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            currentOnEnabled()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/** Opens this app's details page in system settings. */
@Composable
internal actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

/** Sets `View.keepScreenOn` to [enabled] and clears it when the composable leaves composition. */
@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(enabled) {
        view.keepScreenOn = enabled
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }
}
