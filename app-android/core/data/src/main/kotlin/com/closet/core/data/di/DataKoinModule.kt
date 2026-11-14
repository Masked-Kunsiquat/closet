package com.closet.core.data.di

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.closet.core.data.BuildConfig
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.repository.ModelDiscoveryRepository
import com.closet.core.data.repository.OutfitRepository
import com.closet.core.data.repository.RecommendationRepository
import com.closet.core.data.repository.StatsRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.repository.WeatherPreferencesRepository
import com.closet.core.data.repository.WeatherRepository
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.weather.GoogleWeatherClient
import com.closet.core.data.weather.NwsClient
import com.closet.core.data.weather.OpenMeteoClient
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.EmbeddingWork
import com.closet.core.data.worker.EmbeddingWorker
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.worker.ImageCompressionWork
import com.closet.core.data.worker.ImageCompressionWorker
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Koin equivalent of the Hilt `DataModule` (runs alongside it until the Hilt cutover).
 * Every Hilt `@Singleton` is a `single`; no data-layer class is unscoped.
 */
val dataModule = module {
    single<ClothingDatabase> { ClothingDatabase.getDatabase(androidContext()) }
    single<WorkManager> { WorkManager.getInstance(androidContext()) }

    single { get<ClothingDatabase>().clothingDao() }
    single { get<ClothingDatabase>().lookupDao() }
    single { get<ClothingDatabase>().outfitDao() }
    single { get<ClothingDatabase>().logDao() }
    single { get<ClothingDatabase>().statsDao() }
    single { get<ClothingDatabase>().brandDao() }
    single { get<ClothingDatabase>().recommendationDao() }
    single { get<ClothingDatabase>().embeddingDao() }

    single<EmbeddingScheduler> {
        val workManager = get<WorkManager>()
        object : EmbeddingScheduler {
            private val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            private val request = PeriodicWorkRequestBuilder<EmbeddingWorker>(1L, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            override fun schedule() {
                workManager.enqueueUniquePeriodicWork(
                    EmbeddingWork.NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }

            override fun cancel() {
                workManager.cancelUniqueWork(EmbeddingWork.NAME)
            }

            override fun runNow() {
                workManager.enqueueUniqueWork(
                    EmbeddingWork.IMMEDIATE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<EmbeddingWorker>().build(),
                )
            }

            override val workInfo: Flow<WorkInfo?> =
                workManager.getWorkInfosForUniqueWorkFlow(EmbeddingWork.IMMEDIATE_NAME)
                    .map { it.firstOrNull() }
        }
    }

    single<ImageCompressionScheduler> {
        val workManager = get<WorkManager>()
        object : ImageCompressionScheduler {
            private val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()

            override fun schedule() {
                workManager.enqueueUniqueWork(
                    ImageCompressionWork.NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ImageCompressionWorker>()
                        .setConstraints(constraints)
                        .setInitialDelay(30L, TimeUnit.SECONDS)
                        .build(),
                )
            }

            override fun runNow() {
                workManager.enqueueUniqueWork(
                    ImageCompressionWork.IMMEDIATE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ImageCompressionWorker>().build(),
                )
            }

            override val workInfo: Flow<WorkInfo?> =
                workManager.getWorkInfosForUniqueWorkFlow(ImageCompressionWork.IMMEDIATE_NAME)
                    .map { it.firstOrNull() }
        }
    }

    single<Json> {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    single<HttpClient> {
        val json = get<Json>()
        HttpClient(OkHttp) {
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
                    private val sensitiveHeaders = setOf("authorization", "x-api-key")
                    override fun log(message: String) {
                        val redacted = message.lines().joinToString("\n") { line ->
                            val colon = line.indexOf(':')
                            if (colon > 0 && line.substring(0, colon).trim().lowercase() in sensitiveHeaders) {
                                "${line.substring(0, colon)}: <REDACTED>"
                            } else {
                                line
                            }
                        }
                        Timber.tag("Ktor").d(redacted)
                    }
                }
                level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            }
        }
    }

    single<HttpClient>(AiHttpClientQualifier) {
        val json = get<Json>()
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
                requestTimeoutMillis = 120_000
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = object : Logger {
                    private val sensitiveHeaders = setOf("authorization", "x-api-key")
                    override fun log(message: String) {
                        val redacted = message.lines().joinToString("\n") { line ->
                            val colon = line.indexOf(':')
                            if (colon > 0 && line.substring(0, colon).trim().lowercase() in sensitiveHeaders) {
                                "${line.substring(0, colon)}: <REDACTED>"
                            } else {
                                line
                            }
                        }
                        Timber.tag("KtorAI").d(redacted)
                    }
                }
                level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            }
        }
    }

    // Constructed here only (never singleOf): matches DataModule.provideAiPreferencesRepository.
    single { AiPreferencesRepository(androidContext(), get<EncryptedKeyStore>()) }

    singleOf(::EncryptedKeyStore)
    singleOf(::LocationProvider)
    singleOf(::BrandRepository)
    singleOf(::ClothingRepository)
    singleOf(::LogRepository)
    singleOf(::LookupRepository)
    singleOf(::ModelDiscoveryRepository)
    singleOf(::OutfitRepository)
    singleOf(::RecommendationRepository)
    singleOf(::StatsRepository)
    singleOf(::StorageRepository)
    singleOf(::WeatherPreferencesRepository)
    singleOf(::WeatherRepository)
    singleOf(::EmbeddingEncoder)
    singleOf(::EmbeddingIndex)
    singleOf(::GoogleWeatherClient)
    singleOf(::NwsClient)
    singleOf(::OpenMeteoClient)

    workerOf(::EmbeddingWorker)
    workerOf(::ImageCompressionWorker)
}
