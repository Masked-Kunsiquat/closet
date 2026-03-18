package com.closet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import com.closet.BuildConfig

/**
 * The Application class for the Closet app.
 * Required by Hilt for dependency injection.
 */
@HiltAndroidApp
class ClosetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
