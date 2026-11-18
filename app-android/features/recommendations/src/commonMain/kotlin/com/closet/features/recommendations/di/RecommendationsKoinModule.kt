package com.closet.features.recommendations.di

import com.closet.features.recommendations.RecommendationViewModel
import com.closet.features.recommendations.ai.AnthropicProvider
import com.closet.features.recommendations.ai.OpenAiProvider
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the recommendations feature. The on-device provider is bound per flavor:
 * `com.closet.platform.gms.recommendations.NanoProvider` in `gmsModule` (full), or the no-op
 * stub in `recommendationsNoOpModule` (foss).
 */
val recommendationsModule = module {
    singleOf(::OutfitRecommendationEngine)
    singleOf(::AnthropicProvider)
    singleOf(::OpenAiProvider)
    singleOf(::OutfitCoherenceScorer)
    viewModel {
        RecommendationViewModel(
            recommendationRepository = get(),
            engine = get(),
            scorer = get(),
            weatherRepository = get(),
            outfitRepository = get(),
            lookupRepository = get(),
            imagePathResolver = get(),
            aiPrefsRepo = get(),
            clock = get(),
        )
    }
}
