package com.closet.core.data.repository

import java.io.File

/** The Preferences DataStore file for [name] in [directory]: `<directory>/<name>.preferences_pb`. */
fun jvmPreferencesPath(directory: File, name: String): String =
    File(directory, "$name.preferences_pb").absolutePath
