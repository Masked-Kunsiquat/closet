package com.closet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.core.ui.preferences.PreferencesRepository
import com.closet.core.ui.theme.ClosetAccent
import com.closet.core.ui.theme.ClosetTheme
import com.closet.navigation.ClosetNavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Single-activity entry point for the app.
 *
 * Enables edge-to-edge display and hosts [ClosetNavGraph], which owns the root [Scaffold]
 * and bottom navigation bar. Hilt dependency injection is provided via
 * [@AndroidEntryPoint][dagger.hilt.android.AndroidEntryPoint].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Field-injected rather than constructor-injected: Hilt does not support
    // constructor injection for Activity subclasses.
    @Inject lateinit var preferencesRepository: PreferencesRepository

    // Carries the latest shortcut (or regular launch) intent so ClosetNavGraph
    // can react to it in a LaunchedEffect. Null means "no pending navigation".
    private val _shortcutIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Publish the launch intent so the nav graph can route to the correct
        // destination. Only set on a fresh start (savedInstanceState == null)
        // to avoid re-navigating when the activity is recreated (e.g. rotation).
        if (savedInstanceState == null) {
            _shortcutIntent.value = intent
        }

        // Enable edge-to-edge to handle modern system bar behavior
        enableEdgeToEdge()

        setContent {
            val accent = preferencesRepository.getAccent()
                .collectAsStateWithLifecycle(initialValue = ClosetAccent.Amber)
            val dynamicColor = preferencesRepository.getDynamicColor()
                .collectAsStateWithLifecycle(initialValue = false)

            ClosetTheme(accent = accent.value, dynamicColor = dynamicColor.value) {
                ClosetNavGraph(
                    modifier = Modifier.fillMaxSize(),
                    shortcutIntent = _shortcutIntent.asStateFlow(),
                    onShortcutConsumed = { _shortcutIntent.value = null },
                )
            }
        }
    }

    // Called when the activity is already running and a shortcut is tapped again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _shortcutIntent.value = intent
    }
}
