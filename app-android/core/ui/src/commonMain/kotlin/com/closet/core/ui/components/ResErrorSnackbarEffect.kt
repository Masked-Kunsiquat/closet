package com.closet.core.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Shows a Snackbar when [errorRes] is non-null, then calls [onErrorConsumed] to clear it.
 *
 * Use for ViewModels that expose a nullable [StringResource] error field.
 */
@Composable
fun ResErrorSnackbarEffect(
    errorRes: StringResource?,
    snackbarHostState: SnackbarHostState,
    onErrorConsumed: () -> Unit
) {
    LaunchedEffect(errorRes) {
        if (errorRes != null) {
            snackbarHostState.showSnackbar(getString(errorRes))
            onErrorConsumed()
        }
    }
}
