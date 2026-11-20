package com.closet.core.ui.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.closet.core.ui.theme.ClosetAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for persisting and retrieving user preferences via [DataStore].
 *
 * Backed by the `closet_prefs` preferences DataStore bound per platform in Koin. All operations are
 * coroutine-safe; reads are exposed as a [Flow] so callers react to changes
 * automatically without polling.
 *
 * Provided as a Koin `single` — inject anywhere via Koin.
 */
class PreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private val accentKey = stringPreferencesKey("accent")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val lastHandledBatchIdKey = stringPreferencesKey("last_handled_batch_id")
    private val lastHandledColorRedetectIdKey = stringPreferencesKey("last_handled_color_redetect_id")

    /**
     * Returns a [Flow] that emits the currently stored [ClosetAccent] and
     * re-emits whenever it changes. Falls back to [ClosetAccent.Amber] if no
     * value has been saved or the stored name no longer matches a known accent.
     */
    fun getAccent(): Flow<ClosetAccent> = dataStore.data.map { prefs ->
        val name = prefs[accentKey] ?: ClosetAccent.Amber.name
        ClosetAccent.entries.find { it.name == name } ?: ClosetAccent.Amber
    }

    /**
     * Persists [accent] to DataStore. Suspends until the write completes.
     */
    suspend fun setAccent(accent: ClosetAccent) {
        dataStore.edit { prefs ->
            prefs[accentKey] = accent.name
        }
    }

    /**
     * Returns a [Flow] that emits whether Material You dynamic color is enabled.
     * Defaults to `false` so the user-selected accent always applies unless
     * explicitly opted in.
     */
    fun getDynamicColor(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    /**
     * Persists the dynamic color [enabled] flag to DataStore. Suspends until
     * the write completes.
     */
    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    /**
     * The UUID string of the last batch segmentation job the UI acknowledged,
     * used to suppress duplicate snackbars across recompositions. Null when unset.
     */
    val lastHandledBatchId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[lastHandledBatchIdKey]
    }

    suspend fun setLastHandledBatchId(id: String): Result<Unit> = try {
        dataStore.edit { prefs ->
            prefs[lastHandledBatchIdKey] = id
        }
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    /**
     * The id of the last bulk colour re-detect job whose result the UI acknowledged, kept apart
     * from [lastHandledBatchId] so the two snackbars never suppress each other. Null when unset.
     */
    val lastHandledColorRedetectId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[lastHandledColorRedetectIdKey]
    }

    suspend fun setLastHandledColorRedetectId(id: String): Result<Unit> = try {
        dataStore.edit { prefs ->
            prefs[lastHandledColorRedetectIdKey] = id
        }
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }
}
