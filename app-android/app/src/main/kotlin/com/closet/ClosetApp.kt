package com.closet

import android.app.Application
import com.closet.core.data.repository.AiPreferencesRepository
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
 */
@HiltAndroidApp
class ClosetApp : Application() {

    @Inject lateinit var aiPreferencesRepository: AiPreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Migrate any API keys previously stored as plaintext in DataStore to EncryptedKeyStore.
        // No-op after the first run or if keys were never set.
        applicationScope.launch {
            aiPreferencesRepository.migrateKeysFromPlainDataStore()
        }
    }
}
