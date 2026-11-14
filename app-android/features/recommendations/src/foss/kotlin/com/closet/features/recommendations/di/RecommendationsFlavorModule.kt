package com.closet.features.recommendations.di

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.features.recommendations.ai.NanoProvider
import org.koin.dsl.binds
import org.koin.dsl.module

/** foss flavor: no-arg stub NanoProvider. One instance, bound to both interfaces. */
val recommendationsFlavorModule = module {
    single { NanoProvider() } binds arrayOf(OutfitAiProvider::class, NanoInitializer::class)
}
