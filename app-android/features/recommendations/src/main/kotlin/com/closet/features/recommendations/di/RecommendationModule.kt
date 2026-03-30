package com.closet.features.recommendations.di

import com.closet.core.data.ai.NanoInitializer
import com.closet.core.data.ai.OutfitAiProvider
import com.closet.features.recommendations.ai.NanoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the recommendations feature.
 *
 * [com.closet.core.data.repository.RecommendationRepository],
 * [com.closet.core.data.dao.RecommendationDao], and
 * [com.closet.core.data.repository.AiPreferencesRepository] are provided by
 * [com.closet.core.data.di.DataModule] per project convention — all DAOs and
 * repositories that live in core/data are provided there.
 *
 * This module binds the feature-local [NanoProvider] implementation to the
 * [OutfitAiProvider] interface. The binding lives here (not in DataModule) because
 * [NanoProvider] depends on the MLKit GenAI library, which is only declared as a
 * dependency of the recommendations module — not core/data.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecommendationModule {

    /**
     * Binds [NanoProvider] as the default [OutfitAiProvider] implementation.
     *
     * This is the on-device, no-key-required default. Phase 2 will introduce a
     * provider selector that switches between Nano, OpenAI-compatible, and Anthropic
     * implementations at runtime based on [com.closet.core.data.repository.AiPreferencesRepository].
     */
    @Binds
    @Singleton
    abstract fun bindOutfitAiProvider(nanoProvider: NanoProvider): OutfitAiProvider

    /**
     * Binds [NanoProvider] as the [NanoInitializer] implementation.
     *
     * Defined in core/data so features/settings can inject [NanoInitializer] without
     * depending on features/recommendations.
     */
    @Binds
    @Singleton
    abstract fun bindNanoInitializer(nanoProvider: NanoProvider): NanoInitializer
}
