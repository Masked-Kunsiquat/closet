package com.closet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.closet.backup.BackupForegroundService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.EmbeddingScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The Application class for the Closet app.
 * Required by Hilt for dependency injection.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory] instead of the
 * default factory. The default WorkManager initializer is disabled in AndroidManifest.xml;
 * WorkManager initializes on-demand on the first [androidx.work.WorkManager.getInstance] call.
 */
@HiltAndroidApp
class ClosetApp : Application(), Configuration.Provider {

    @Inject lateinit var aiPreferencesRepository: AiPreferencesRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var embeddingScheduler: EmbeddingScheduler
    @Inject lateinit var embeddingIndex: EmbeddingIndex

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                BackupForegroundService.CHANNEL_ID,
                "Backup & Restore",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress notifications for backup and restore operations"
            },
        )
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannels()
        // Register the periodic embedding worker (charging + idle; no-op if already queued).
        embeddingScheduler.schedule()

        // Pre-load embedding index so the chat screen is warm on first open.
        applicationScope.launch {
            runCatching { embeddingIndex.load() }
                .onFailure { Timber.tag("ClosetApp").e(it, "EmbeddingIndex load failed — chat retrieval will be unavailable until next launch") }
        }

        // Migrate any API keys previously stored as plaintext in DataStore to EncryptedKeyStore.
        // No-op after the first run or if keys were never set.
        applicationScope.launch {
            runCatching { aiPreferencesRepository.migrateKeysFromPlainDataStore() }
                .onFailure { Timber.tag("ClosetApp").e(it, "Key migration failed — app will continue without migrated keys") }
        }
    }
}
