package com.closet.core.ui.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.data.repository.iosPreferencesPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** iOS UI definitions: the `closet_prefs` DataStore lives in the app's documents directory. */
val iosUiModule = module {
    includes(commonUiModule)
    single<DataStore<Preferences>>(named(CLOSET_PREFS_NAME)) {
        createPreferencesDataStore(iosPreferencesPath(CLOSET_PREFS_NAME))
    }
}
