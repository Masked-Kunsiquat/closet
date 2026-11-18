package com.closet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.closet.backup.backupScreen
import com.closet.backup.navigateToBackup
import com.closet.shared.App
import com.closet.shared.ShortcutRequest
import com.closet.shortcuts.toAppShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-activity entry point for the app.
 *
 * Enables edge-to-edge display and hosts the shared [App], which owns the theme, the root
 * Scaffold, the bottom navigation bar and the NavHost. Android-only destinations (backup)
 * and launcher-shortcut intents are supplied from here.
 */
class MainActivity : ComponentActivity() {

    // Latest launcher-shortcut request. Null means "no pending navigation". Each delivery is a
    // new ShortcutRequest, so tapping the same shortcut twice in quick succession routes twice.
    private val _pendingShortcut = MutableStateFlow<ShortcutRequest?>(null)
    private val pendingShortcut = _pendingShortcut.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only on a fresh start (savedInstanceState == null), so rotation doesn't re-navigate.
        if (savedInstanceState == null) {
            _pendingShortcut.value = intent.toAppShortcut()?.let(::ShortcutRequest)
        }

        enableEdgeToEdge()

        setContent {
            App(
                modifier = Modifier.fillMaxSize(),
                pendingShortcut = pendingShortcut,
                onShortcutConsumed = { _pendingShortcut.value = null },
                onNavigateToBackup = { it.navigateToBackup() },
                platformDestinations = { nav ->
                    backupScreen(
                        onNavigateUp = { nav.popBackStack() },
                    )
                },
            )
        }
    }

    // Called when the activity is already running and a shortcut is tapped again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _pendingShortcut.value = intent.toAppShortcut()?.let(::ShortcutRequest)
    }
}
