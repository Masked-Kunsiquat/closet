package com.closet.features.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@Composable
internal actual fun rememberPhotoPicker(onPicked: (String?) -> Unit): () -> Unit = {}

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(StringSelection(text))
