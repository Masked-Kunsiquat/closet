package com.closet.di

import com.closet.backup.BackupRepository
import com.closet.backup.BackupViewModel
import com.closet.backup.RestoreRepository
import com.closet.core.data.di.dataModule
import com.closet.core.ui.di.uiModule
import com.closet.features.chat.di.chatModule
import com.closet.features.outfits.di.outfitsModule
import com.closet.features.recommendations.di.recommendationsModule
import com.closet.features.settings.di.settingsModule
import com.closet.features.stats.di.statsModule
import com.closet.features.wardrobe.di.wardrobeModule
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::BackupRepository)
    singleOf(::RestoreRepository)
    viewModelOf(::BackupViewModel)
}

val allModules = listOf(
    dataModule, uiModule, statsModule, settingsModule, outfitsModule,
    recommendationsModule, chatModule, wardrobeModule,
    flavorModule, // per-flavor: gmsModule (full) or the features' *NoOpModules (foss)
    appModule,
)
