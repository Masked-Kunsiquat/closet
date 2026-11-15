package com.closet.core.data.di

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.closet.core.data.BuildFlags
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
import co.touchlab.kermit.Logger as KermitLogger
import java.util.concurrent.TimeUnit

/**
 * Koin definitions for the data layer. Every data-layer class is a `single`;
 * none is unscoped.
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
        buildHttpClient(get(), isDebug = get<BuildFlags>().isDebug, socketTimeoutMs = 30_000, requestTimeoutMs = 30_000, logTag = "Ktor")
    }

    single {
        AiHttpClient(
            buildHttpClient(get(), isDebug = get<BuildFlags>().isDebug, socketTimeoutMs = 120_000, requestTimeoutMs = 120_000, logTag = "KtorAI"),
        )
    }

    singleOf(::AiPreferencesRepository)
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

/** Headers whose values are replaced with `<REDACTED>` in debug HTTP logs. */
private val SENSITIVE_HEADERS = setOf("authorization", "x-api-key", "x-goog-api-key")

/**
 * Builds an OkHttp-backed [HttpClient] with JSON content negotiation and debug-only
 * logging (when [isDebug]) that redacts [SENSITIVE_HEADERS]. Connect timeout is always 30 s.
 */
private fun buildHttpClient(
    json: Json,
    isDebug: Boolean,
    socketTimeoutMs: Long,
    requestTimeoutMs: Long,
    logTag: String,
): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = socketTimeoutMs
        requestTimeoutMillis = requestTimeoutMs
    }
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                val redacted = message.lines().joinToString("\n") { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0 && line.substring(0, colon).trim().lowercase() in SENSITIVE_HEADERS) {
                        "${line.substring(0, colon)}: <REDACTED>"
                    } else {
                        line
                    }
                }
                KermitLogger.withTag(logTag).d { redacted }
            }
        }
        level = if (isDebug) LogLevel.BODY else LogLevel.NONE
    }
}
