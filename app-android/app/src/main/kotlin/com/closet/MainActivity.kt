package com.closet

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge to handle modern system bar behavior
        enableEdgeToEdge()

        setContent {
            val accent = preferencesRepository.getAccent()
                .collectAsStateWithLifecycle(initialValue = ClosetAccent.Amber)
            val dynamicColor = preferencesRepository.getDynamicColor()
                .collectAsStateWithLifecycle(initialValue = false)

            ClosetTheme(accent = accent.value, dynamicColor = dynamicColor.value) {
                ClosetNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
