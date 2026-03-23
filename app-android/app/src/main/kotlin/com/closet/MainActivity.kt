package com.closet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.closet.core.ui.theme.ClosetTheme
import com.closet.navigation.ClosetNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for the app.
 *
 * Enables edge-to-edge display and hosts [ClosetNavGraph], which owns the root [Scaffold]
 * and bottom navigation bar. Hilt dependency injection is provided via
 * [@AndroidEntryPoint][dagger.hilt.android.AndroidEntryPoint].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge to handle modern system bar behavior
        enableEdgeToEdge()

        setContent {
            ClosetTheme {
                ClosetNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
