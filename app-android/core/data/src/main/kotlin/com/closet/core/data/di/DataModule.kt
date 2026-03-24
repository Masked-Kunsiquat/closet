package com.closet.core.data.di

import android.content.Context
import com.closet.core.data.BuildConfig
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.*
import com.closet.core.data.repository.WeatherPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module that provides the Room database, all DAO singletons, and
 * DataStore-backed repositories that live in the data layer.
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

    /** Provides the [WeatherPreferencesRepository] singleton backed by its own DataStore file. */
    @Provides
    @Singleton
    fun provideWeatherPreferencesRepository(
        @ApplicationContext context: Context,
    ): WeatherPreferencesRepository = WeatherPreferencesRepository(context)

    /**
     * Provides the shared [HttpClient] singleton used by all weather service clients.
     *
     * - Engine: OkHttp (recommended Android engine for Ktor 3.x).
     * - ContentNegotiation: kotlinx-serialization JSON with unknown-key tolerance so
     *   future API fields don't crash the parser.
     * - Logging: Timber-backed, bodies only in debug builds to avoid leaking API keys
     *   or location data in release logs.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = Timber.tag("Ktor").d(message)
            }
            level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
        }
    }
}
