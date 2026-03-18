package com.closet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The Application class for the Closet app.
 * Required by Hilt for dependency injection.
 */
@HiltAndroidApp
class ClosetApp : Application()
