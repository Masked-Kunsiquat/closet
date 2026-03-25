package com.closet.features.recommendations.di

import com.closet.core.data.dao.RecommendationDao
import com.closet.core.data.repository.RecommendationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the recommendations feature.
 *
 * [RecommendationDao] is provided by [com.closet.core.data.di.DataModule] from the
 * shared [com.closet.core.data.ClothingDatabase] instance. This module binds
 * [RecommendationRepository] so ViewModels and the engine can inject it via Hilt
 * without a direct dependency on the core/data DI module internals.
 *
 * Installed in [SingletonComponent] so the repository shares the same database
 * instance for the app's lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecommendationModule {

    /**
     * Provides the [RecommendationRepository] singleton.
     *
     * [RecommendationDao] is resolved from [com.closet.core.data.di.DataModule]
     * which is also installed in [SingletonComponent].
     */
    @Provides
    @Singleton
    fun provideRecommendationRepository(
        recommendationDao: RecommendationDao
    ): RecommendationRepository = RecommendationRepository(recommendationDao)
}
