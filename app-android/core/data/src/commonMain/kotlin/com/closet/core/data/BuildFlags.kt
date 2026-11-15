package com.closet.core.data

/**
 * Build-time flags supplied by the application module.
 *
 * `core/data` is a Kotlin Multiplatform library and has no `BuildConfig`, so `app`
 * binds this in Koin (`single { BuildFlags(BuildConfig.DEBUG) }`) and data-layer code
 * reads it instead.
 */
data class BuildFlags(val isDebug: Boolean)
