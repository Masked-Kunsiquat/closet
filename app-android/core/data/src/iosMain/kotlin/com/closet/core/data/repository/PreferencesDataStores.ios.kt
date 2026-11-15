package com.closet.core.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** The Preferences DataStore file for [name] in the app's documents directory. */
fun iosPreferencesPath(name: String): String = documentDirectory() + "/$name.preferences_pb"

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(directory?.path) { "No documents directory" }
}
