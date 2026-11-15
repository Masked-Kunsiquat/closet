package com.closet.features.recommendations.di

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.features.recommendations.ai.NanoProvider
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import org.koin.dsl.binds
import org.koin.dsl.module

/** full flavor: NanoProvider(json) backed by MLKit GenAI. One instance, bound to both interfaces. */
val recommendationsFlavorModule = module {
    single<OnDeviceRecommendationProvider> { NanoProvider(get()) } binds arrayOf(OutfitAiProvider::class, NanoInitializer::class)
}
