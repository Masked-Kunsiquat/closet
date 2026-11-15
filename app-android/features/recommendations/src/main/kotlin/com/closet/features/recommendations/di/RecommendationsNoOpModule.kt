package com.closet.features.recommendations.di

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.features.recommendations.ai.OnDeviceRecommendationProvider
import com.closet.features.recommendations.ai.UnavailableOnDeviceRecommendationProvider
import org.koin.dsl.binds
import org.koin.dsl.module

/** No-op capability bindings for builds without Play Services. One instance, bound to all interfaces. */
val recommendationsNoOpModule = module {
    single<OnDeviceRecommendationProvider> { UnavailableOnDeviceRecommendationProvider() } binds arrayOf(OutfitAiProvider::class, NanoInitializer::class)
}
