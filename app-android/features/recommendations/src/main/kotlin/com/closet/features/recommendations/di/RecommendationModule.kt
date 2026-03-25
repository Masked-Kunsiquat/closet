package com.closet.features.recommendations.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the recommendations feature.
 *
 * [com.closet.core.data.repository.RecommendationRepository] and
 * [com.closet.core.data.dao.RecommendationDao] are provided by
 * [com.closet.core.data.di.DataModule] per project convention — all DAOs and
 * repositories that live in core/data are provided there.
 *
 * This module is reserved for future feature-level bindings (e.g. use-case
 * classes or mappers that are specific to this feature module).
 */
@Module
@InstallIn(SingletonComponent::class)
object RecommendationModule
