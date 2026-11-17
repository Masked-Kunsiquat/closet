package com.closet.features.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry

/**
 * Returns a launcher that opens the platform image picker; [onPicked] receives the chosen image's
 * URI string, or null if the user cancelled. JVM and iOS have no picker yet and return a no-op.
 */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (String?) -> Unit): () -> Unit

/** Intercepts the system back gesture while [enabled]. A no-op on JVM and iOS. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** A clipboard entry holding [text] as plain text. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
