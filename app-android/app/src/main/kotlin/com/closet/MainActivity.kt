package com.closet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.closet.core.ui.theme.ClosetTheme
import com.closet.navigation.ClosetNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for the app.
 *
 * Enables edge-to-edge display and hosts [ClosetNavGraph] inside a [ClosetTheme]-wrapped
 * [androidx.compose.material3.Scaffold]. Hilt dependency injection is provided via
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClosetNavGraph()
                }
            }
        }
    }
}
