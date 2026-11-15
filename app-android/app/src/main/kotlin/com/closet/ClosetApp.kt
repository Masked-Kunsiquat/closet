package com.closet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.closet.backup.BackupForegroundService
import com.closet.core.data.repository.AiPreferencesRepository
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.EmbeddingScheduler
import com.closet.core.data.worker.ImageCompressionScheduler
import com.closet.di.allModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

/**
 * The Application class for the Closet app.
 *
 * Starts Koin in [onCreate] with [allModules]. `workManagerFactory()` registers Koin's
 * worker factory with WorkManager, so the default WorkManager initializer is disabled in
 * AndroidManifest.xml.
 */
class ClosetApp : Application() {

    private val aiPreferencesRepository: AiPreferencesRepository by inject()
    private val embeddingScheduler: EmbeddingScheduler by inject()
    private val embeddingIndex: EmbeddingIndex by inject()
    private val imageCompressionScheduler: ImageCompressionScheduler by inject()

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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@ClosetApp)
            workManagerFactory()
            modules(allModules)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannels()
        // Register the periodic embedding worker (charging + idle; no-op if already queued).
        embeddingScheduler.schedule()
        // Schedule background image compression (idle + battery-not-low, 30 s delay; KEEP — no-op if queued).
        imageCompressionScheduler.schedule()

        // Pre-load embedding index so the chat screen is warm on first open.
        applicationScope.launch {
            runCatching { embeddingIndex.load() }
                .onFailure { Timber.tag("ClosetApp").e(it, "EmbeddingIndex load failed — chat retrieval will be unavailable until next launch") }
        }

        // Resolve on the main thread, outside runCatching: if EncryptedKeyStore can't open even
        // after recreating its file, the app must fail here at launch, not later on a screen.
        val aiPrefs = aiPreferencesRepository

        // Migrate any API keys previously stored as plaintext in DataStore to EncryptedKeyStore.
        // No-op after the first run or if keys were never set.
        applicationScope.launch {
            runCatching { aiPrefs.migrateKeysFromPlainDataStore() }
                .onFailure { Timber.tag("ClosetApp").e(it, "Key migration failed — app will continue without migrated keys") }
        }
    }
}
