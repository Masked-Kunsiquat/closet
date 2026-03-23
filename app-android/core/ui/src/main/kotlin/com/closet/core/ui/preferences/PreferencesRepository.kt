package com.closet.core.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.closet.core.ui.theme.ClosetAccent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "closet_prefs")

/**
 * Repository for persisting and retrieving user preferences via [DataStore].
 *
 * Backed by a `preferences` DataStore named `closet_prefs`. All operations are
 * coroutine-safe; reads are exposed as a [Flow] so callers react to changes
 * automatically without polling.
 *
 * Provided as a [Singleton] — inject anywhere via Hilt.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accentKey = stringPreferencesKey("accent")

    /**
     * Returns a [Flow] that emits the currently stored [ClosetAccent] and
     * re-emits whenever it changes. Falls back to [ClosetAccent.Amber] if no
     * value has been saved or the stored name no longer matches a known accent.
     */
    fun getAccent(): Flow<ClosetAccent> = context.dataStore.data.map { prefs ->
        val name = prefs[accentKey] ?: ClosetAccent.Amber.name
        ClosetAccent.entries.find { it.name == name } ?: ClosetAccent.Amber
    }

    /**
     * Persists [accent] to DataStore. Suspends until the write completes.
     */
    suspend fun setAccent(accent: ClosetAccent) {
        context.dataStore.edit { prefs ->
            prefs[accentKey] = accent.name
        }
    }
}
