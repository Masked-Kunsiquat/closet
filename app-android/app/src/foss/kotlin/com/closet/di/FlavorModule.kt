package com.closet.di

import com.closet.features.chat.di.chatNoOpModule
import com.closet.features.recommendations.di.recommendationsNoOpModule
import com.closet.features.wardrobe.di.wardrobeNoOpModule
import org.koin.dsl.module

/** foss flavor: the features' pure-Kotlin no-op capability bindings; no GMS. */
val flavorModule = module {
    includes(chatNoOpModule, recommendationsNoOpModule, wardrobeNoOpModule)
}
