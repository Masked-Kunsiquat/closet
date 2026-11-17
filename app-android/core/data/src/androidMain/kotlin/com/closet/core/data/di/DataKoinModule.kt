package com.closet.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.getDatabase
import com.closet.core.data.location.AndroidLocationProvider
import com.closet.core.data.location.LocationProvider
import com.closet.core.data.repository.AI_PREFS_NAME
import com.closet.core.data.repository.EncryptedKeyStore
import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.repository.ImageStorageStats
import com.closet.core.data.shortcuts.AndroidShortcutUsageReporter
import com.closet.core.data.shortcuts.ShortcutUsageReporter
import com.closet.core.data.repository.SecretStore
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.repository.WEATHER_PREFS_NAME
import com.closet.core.data.repository.androidPreferencesPath
import com.closet.core.data.repository.createPreferencesDataStore
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.OnnxEmbeddingEncoder
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.EmbeddingWork
import com.closet.core.data.worker.EmbeddingWorker
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.core.data.worker.ImageCompressionWork
import com.closet.core.data.worker.ImageCompressionWorker
import com.closet.core.data.worker.WorkStatus
import com.closet.core.data.worker.toWorkStatus
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Android-only data-layer definitions: everything that needs a `Context`, WorkManager, the
 * OkHttp engine, the workers, and the Android-backed `SecretStore`, `LocationProvider` and
 * DataStores. Every one is a `single`; none is unscoped.
 */
val androidDataModule = module {
    single<ClothingDatabase> { ClothingDatabase.getDatabase(androidContext()) }
    single<WorkManager> { WorkManager.getInstance(androidContext()) }


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

            override val workStatus: Flow<WorkStatus?> =
                workManager.getWorkInfosForUniqueWorkFlow(EmbeddingWork.IMMEDIATE_NAME)
                    .map { it.firstOrNull()?.toWorkStatus() }
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

            override val workStatus: Flow<WorkStatus?> =
                workManager.getWorkInfosForUniqueWorkFlow(ImageCompressionWork.IMMEDIATE_NAME)
                    .map { it.firstOrNull()?.toWorkStatus() }
        }
    }

    single<HttpClientEngineFactory<*>> { OkHttp }

    // One DataStore instance per file per process: every ai_prefs / weather_prefs access goes
    // through these two singles. The paths are the ones preferencesDataStore(name) used.
    single<DataStore<Preferences>>(named(AI_PREFS_NAME)) {
        createPreferencesDataStore(androidPreferencesPath(androidContext().filesDir, AI_PREFS_NAME))
    }
    single<DataStore<Preferences>>(named(WEATHER_PREFS_NAME)) {
        createPreferencesDataStore(androidPreferencesPath(androidContext().filesDir, WEATHER_PREFS_NAME))
    }
    single<SecretStore> { EncryptedKeyStore(androidContext()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single<ShortcutUsageReporter> { AndroidShortcutUsageReporter(androidContext()) }
    singleOf(::StorageRepository) {
        bind<ImagePathResolver>()
        bind<ImageStorageStats>()
    }
    single<EmbeddingEncoder> { OnnxEmbeddingEncoder(androidContext()) }

    workerOf(::EmbeddingWorker)
    workerOf(::ImageCompressionWorker)
}

/**
 * The Android data layer: [commonDataModule] plus [androidDataModule]. This is the module
 * `allModules` and the feature tests load.
 *
 * Declared after [androidDataModule]: top-level vals in one file initialize in order.
 */
val dataModule = module {
    includes(commonDataModule, androidDataModule)
}
