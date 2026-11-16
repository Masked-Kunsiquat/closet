package com.closet.core.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.closet.core.ui.util.UserMessage
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString

/**
 * Collects a [Flow] of [UserMessage]s and shows each as a Snackbar.
 *
 * Place this alongside the [SnackbarHost] in your Scaffold:
 * ```
 * val snackbarHostState = remember { SnackbarHostState() }
 * UserMessageSnackbarEffect(viewModel.actionError, snackbarHostState)
 * Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { ... }
 * ```
 */
@Composable
fun UserMessageSnackbarEffect(
    messages: Flow<UserMessage>,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbarHostState.showSnackbar(getString(message.res, *message.args.toTypedArray()))
        }
    }
}

/**
 * Shows a Snackbar when [errorMessage] is non-null, then calls [onErrorConsumed] to clear it.
 *
 * Use for ViewModels that expose a nullable [String] error field.
 */
@Composable
fun StringErrorSnackbarEffect(
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onErrorConsumed: () -> Unit
) {
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorConsumed()
        }
    }
}
