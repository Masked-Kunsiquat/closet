package com.closet.features.stats.di

import com.closet.features.stats.StatsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val statsModule = module {
    viewModelOf(::StatsViewModel)
}
