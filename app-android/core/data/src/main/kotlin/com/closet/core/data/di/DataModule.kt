package com.closet.core.data.di

import android.content.Context
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the Room database and all DAO singletons.
 *
 * Installed in [dagger.hilt.components.SingletonComponent] so every DAO and
 * repository shares a single [ClothingDatabase] instance for the app's lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Provides the single [ClothingDatabase] instance for the application. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ClothingDatabase {
        return ClothingDatabase.getDatabase(context)
    }

    /** Provides the [ClothingDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideClothingDao(db: ClothingDatabase): ClothingDao = db.clothingDao()

    /** Provides the [LookupDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideLookupDao(db: ClothingDatabase): LookupDao = db.lookupDao()

    /** Provides the [OutfitDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideOutfitDao(db: ClothingDatabase): OutfitDao = db.outfitDao()

    /** Provides the [LogDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideLogDao(db: ClothingDatabase): LogDao = db.logDao()

    /** Provides the [StatsDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideStatsDao(db: ClothingDatabase): StatsDao = db.statsDao()

    /** Provides the [BrandDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideBrandDao(db: ClothingDatabase): BrandDao = db.brandDao()
}
