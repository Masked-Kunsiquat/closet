package com.closet.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/** File name (without extension) of the AI preferences DataStore; also its Koin qualifier. */
const val AI_PREFS_NAME = "ai_prefs"

/** File name (without extension) of the weather preferences DataStore; also its Koin qualifier. */
const val WEATHER_PREFS_NAME = "weather_prefs"

/**
 * Opens a Preferences DataStore on the file at [path] (absolute, ending in `.preferences_pb`).
 *
 * DataStore allows only one instance per file per process, so create each store once (a Koin
 * `single`) and share it.
 */
fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/**
 * Like [createPreferencesDataStore], but the store runs in [scope]. Cancelling [scope] closes the
 * store and releases the file, so tests can reopen it with a new instance.
 */
fun createPreferencesDataStore(path: String, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path.toPath() })
