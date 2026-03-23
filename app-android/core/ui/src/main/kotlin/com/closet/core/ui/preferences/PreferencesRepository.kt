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

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accentKey = stringPreferencesKey("accent")

    fun getAccent(): Flow<ClosetAccent> = context.dataStore.data.map { prefs ->
        val name = prefs[accentKey] ?: ClosetAccent.Amber.name
        ClosetAccent.entries.find { it.name == name } ?: ClosetAccent.Amber
    }

    suspend fun setAccent(accent: ClosetAccent) {
        context.dataStore.edit { prefs ->
            prefs[accentKey] = accent.name
        }
    }
}
