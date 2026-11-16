package com.closet.core.ui.di

import com.closet.core.ui.preferences.PreferencesRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** File name (without extension) of the preferences DataStore; also its Koin qualifier. */
const val CLOSET_PREFS_NAME = "closet_prefs"

/**
 * Platform-independent UI definitions. Each platform module binds the `closet_prefs`
 * `DataStore<Preferences>` and includes this module.
 */
val commonUiModule = module {
    single { PreferencesRepository(get(named(CLOSET_PREFS_NAME))) }
}
