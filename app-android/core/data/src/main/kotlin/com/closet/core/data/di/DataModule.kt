package com.closet.core.data.di

import android.content.Context
import com.closet.core.data.BuildConfig
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.*
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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

    /** Provides the [RecommendationDao] singleton from the shared database instance. */
    @Provides
    @Singleton
    fun provideRecommendationDao(db: ClothingDatabase): RecommendationDao = db.recommendationDao()

    /** Provides the [RecommendationRepository] singleton. */
    @Provides
    @Singleton
    fun provideRecommendationRepository(
        recommendationDao: RecommendationDao,
    ): RecommendationRepository = RecommendationRepository(recommendationDao)

    /** Provides the [WeatherPreferencesRepository] singleton backed by its own DataStore file. */
    @Provides
    @Singleton
    fun provideWeatherPreferencesRepository(
        @ApplicationContext context: Context,
    ): WeatherPreferencesRepository = WeatherPreferencesRepository(context)

    /** Provides the [AiPreferencesRepository] singleton backed by its own DataStore file. */
    @Provides
    @Singleton
    fun provideAiPreferencesRepository(
        @ApplicationContext context: Context,
    ): AiPreferencesRepository = AiPreferencesRepository(context)

    /**
     * Provides the shared [Json] instance used for both HTTP content negotiation
     * and DataStore cache serialization.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Provides the shared [HttpClient] singleton used by all weather service clients.
     *
     * - Engine: OkHttp (recommended Android engine for Ktor 3.x).
     * - ContentNegotiation: shares the [provideJson] instance so HTTP parsing and
     *   cache serialization use the same config.
     * - Logging: Timber-backed, bodies only in debug builds to avoid leaking API keys
     *   or location data in release logs.
     */
    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = Timber.tag("Ktor").d(message)
            }
            level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
        }
    }
}
