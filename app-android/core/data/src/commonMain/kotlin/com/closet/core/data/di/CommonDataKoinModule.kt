package com.closet.core.data.di

import com.closet.core.data.BuildFlags
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.repository.AI_PREFS_NAME
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.repository.StatsRepository
import com.closet.core.data.repository.WEATHER_PREFS_NAME
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.weather.GoogleWeatherClient
import com.closet.core.data.weather.NwsClient
import com.closet.core.data.weather.OpenMeteoClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The platform-neutral data-layer definitions. Every one is a `single`.
 *
 * A platform module must also be loaded to supply what this one needs: the
 * [ClothingDatabase], the HTTP engine (`HttpClientEngineFactory<*>`), the two named
 * Preferences DataStores, a `SecretStore` and a `LocationProvider`. On Android that is
 * `androidDataModule` (both are included by `dataModule`); on JVM and iOS it is
 * `jvmDataModule` / `iosDataModule`. [BuildFlags] is bound by the app.
 *
 * The platform module also binds the `EmbeddingEncoder` and the `EmbeddingScheduler` /
 * `ImageCompressionScheduler` that features use. `BatchSegmentationScheduler` is bound by
 * `wardrobeModule` on Android and by the platform module on JVM and iOS.
 */
val commonDataModule = module {
    single { get<ClothingDatabase>().clothingDao() }
    single { get<ClothingDatabase>().lookupDao() }
    single { get<ClothingDatabase>().outfitDao() }
    single { get<ClothingDatabase>().logDao() }
    single { get<ClothingDatabase>().statsDao() }
    single { get<ClothingDatabase>().brandDao() }
    single { get<ClothingDatabase>().recommendationDao() }
    single { get<ClothingDatabase>().embeddingDao() }

    single<Json> {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    single<HttpClient> {
        buildHttpClient(
            engine = get<HttpClientEngineFactory<*>>(),
            json = get(),
            socketTimeoutMs = 30_000,
            requestTimeoutMs = 30_000,
            logTag = "Ktor",
            isDebug = get<BuildFlags>().isDebug,
        )
    }

    single {
        AiHttpClient(
            buildHttpClient(
                engine = get<HttpClientEngineFactory<*>>(),
                json = get(),
                socketTimeoutMs = 120_000,
                requestTimeoutMs = 120_000,
                logTag = "KtorAI",
                isDebug = get<BuildFlags>().isDebug,
            ),
        )
    }

    single { AiPreferencesRepository(get(named(AI_PREFS_NAME)), get()) }
    singleOf(::BrandRepository)
    singleOf(::ClothingRepository)
    singleOf(::LogRepository)
    singleOf(::LookupRepository)
    singleOf(::ModelDiscoveryRepository)
    singleOf(::OutfitRepository)
    singleOf(::RecommendationRepository)
    singleOf(::StatsRepository)
    single { WeatherPreferencesRepository(get(named(WEATHER_PREFS_NAME))) }
    singleOf(::WeatherRepository)
    singleOf(::GoogleWeatherClient)
    singleOf(::NwsClient)
    singleOf(::OpenMeteoClient)
    singleOf(::EmbeddingIndex)
}
