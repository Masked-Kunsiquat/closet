package com.closet.core.data.repository

import java.io.File

/**
 * The file `preferencesDataStore(name)` used before the KMP move:
 * `filesDir/datastore/<name>.preferences_pb`. Opening the store here keeps existing settings, and
 * `BackupRepository` / `RestoreRepository` copy the same files.
 */
fun androidPreferencesPath(filesDir: File, name: String): String =
    File(filesDir, "datastore/$name.preferences_pb").absolutePath
