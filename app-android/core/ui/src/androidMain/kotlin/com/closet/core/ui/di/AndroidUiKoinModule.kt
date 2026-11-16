package com.closet.core.ui.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.repository.androidPreferencesPath
import com.closet.core.data.repository.createPreferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Android UI definitions: the `closet_prefs` DataStore in the app's files directory. */
val androidUiModule = module {
    single<DataStore<Preferences>>(named(CLOSET_PREFS_NAME)) {
        createPreferencesDataStore(androidPreferencesPath(androidContext().filesDir, CLOSET_PREFS_NAME))
    }
}

/** The UI module for the Android app: [commonUiModule] plus [androidUiModule]. */
val uiModule = module {
    includes(commonUiModule, androidUiModule)
}
