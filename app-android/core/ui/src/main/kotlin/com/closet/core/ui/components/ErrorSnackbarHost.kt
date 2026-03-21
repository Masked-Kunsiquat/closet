package com.closet.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.closet.core.ui.util.UserMessage
import kotlinx.coroutines.flow.Flow

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
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val text = if (message.args.isEmpty()) {
                context.getString(message.resId)
            } else {
                context.getString(message.resId, *message.args)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
}

/**
 * Shows a Snackbar when [errorRes] is non-null, then calls [onErrorConsumed] to clear it.
 *
 * Use for ViewModels that expose a nullable `@StringRes Int?` error field.
 */
@Composable
fun ResErrorSnackbarEffect(
    @StringRes errorRes: Int?,
    snackbarHostState: SnackbarHostState,
    onErrorConsumed: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(errorRes) {
        if (errorRes != null) {
            snackbarHostState.showSnackbar(context.getString(errorRes))
            onErrorConsumed()
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
