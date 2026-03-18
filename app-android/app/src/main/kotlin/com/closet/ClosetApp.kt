package com.closet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * The Application class for the Closet app.
 * Required by Hilt for dependency injection.
 */
@HiltAndroidApp
class ClosetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // BuildConfig is generated in the same package as defined in build.gradle.kts (com.closet)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
