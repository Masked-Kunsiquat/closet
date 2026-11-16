package com.closet.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources

/**
 * Shows a Snackbar when [errorRes] is non-null, then calls [onErrorConsumed] to clear it.
 *
 * Use for ViewModels that expose a nullable `@StringRes Int?` error field. Android-only:
 * the ids come from a feature module's own Android `R` until Phase 5.
 */
@Composable
fun ResErrorSnackbarEffect(
    @StringRes errorRes: Int?,
    snackbarHostState: SnackbarHostState,
    onErrorConsumed: () -> Unit
) {
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(errorRes) {
        if (errorRes != null) {
            snackbarHostState.showSnackbar(resources.getString(errorRes))
            onErrorConsumed()
        }
    }
}
