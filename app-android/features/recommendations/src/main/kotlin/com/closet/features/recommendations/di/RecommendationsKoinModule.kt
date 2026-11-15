package com.closet.features.recommendations.di

import com.closet.features.recommendations.RecommendationViewModel
import com.closet.features.recommendations.ai.AnthropicProvider
import com.closet.features.recommendations.ai.OpenAiProvider
import com.closet.features.recommendations.ai.OutfitCoherenceScorer
import com.closet.features.recommendations.engine.OutfitRecommendationEngine
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the recommendations feature. The flavor-specific NanoProvider lives in
 * `recommendationsFlavorModule` (src/full and src/foss).
 */
val recommendationsModule = module {
    singleOf(::OutfitRecommendationEngine)
    singleOf(::AnthropicProvider)
    singleOf(::OpenAiProvider)
    singleOf(::OutfitCoherenceScorer)
    viewModelOf(::RecommendationViewModel)
}
