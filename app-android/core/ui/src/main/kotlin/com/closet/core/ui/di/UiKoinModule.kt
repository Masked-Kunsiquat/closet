package com.closet.core.ui.di

import com.closet.core.ui.preferences.PreferencesRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val uiModule = module {
    singleOf(::PreferencesRepository)
}
