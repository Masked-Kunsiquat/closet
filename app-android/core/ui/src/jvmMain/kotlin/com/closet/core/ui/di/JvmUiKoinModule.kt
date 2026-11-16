package com.closet.core.ui.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.data.repository.jvmPreferencesPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/** JVM UI definitions: the `closet_prefs` DataStore lives in [dataDirectory]. */
fun jvmUiModule(dataDirectory: File): Module = module {
    includes(commonUiModule)
    single<DataStore<Preferences>>(named(CLOSET_PREFS_NAME)) {
        createPreferencesDataStore(jvmPreferencesPath(dataDirectory, CLOSET_PREFS_NAME))
    }
}
