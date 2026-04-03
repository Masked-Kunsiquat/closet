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
    private val dynamicColorKey = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
    private val lastHandledBatchIdKey = stringPreferencesKey("last_handled_batch_id")

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

    /**
     * Returns a [Flow] that emits whether Material You dynamic color is enabled.
     * Defaults to `false` so the user-selected accent always applies unless
     * explicitly opted in.
     */
    fun getDynamicColor(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    /**
     * Persists the dynamic color [enabled] flag to DataStore. Suspends until
     * the write completes.
     */
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    /**
     * The UUID string of the last batch segmentation job the UI acknowledged,
     * used to suppress duplicate snackbars across recompositions. Null when unset.
     */
    val lastHandledBatchId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[lastHandledBatchIdKey]
    }

    suspend fun setLastHandledBatchId(id: String): Result<Unit> = try {
        context.dataStore.edit { prefs ->
            prefs[lastHandledBatchIdKey] = id
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
