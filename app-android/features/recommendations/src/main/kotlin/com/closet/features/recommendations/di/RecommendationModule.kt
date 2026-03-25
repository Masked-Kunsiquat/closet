package com.closet.features.recommendations.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the recommendations feature.
 *
 * DAO and repository bindings are added here as the data layer is built out
 * (see roadmap: "Data layer — engine inputs"). The module is installed in
 * [SingletonComponent] so all recommendation dependencies share the same
 * database instance that [com.closet.core.data.di.DataModule] provides.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecommendationModule
