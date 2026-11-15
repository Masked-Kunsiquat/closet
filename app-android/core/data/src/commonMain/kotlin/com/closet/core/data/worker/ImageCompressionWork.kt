package com.closet.core.data.worker

import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between the image compression worker and any observer so neither module
 * needs to depend on the other.
 */
object ImageCompressionWork {
    /** Unique work name for the auto-scheduled background run (idle + battery-not-low). */
    const val NAME = "image_compression_worker"

    /**
     * Unique work name for user-triggered immediate runs (no constraints).
     * Separate from [NAME] so it doesn't interfere with the auto-scheduled job.
     */
    const val IMMEDIATE_NAME = "image_compression_immediate"

    /** Progress / output key: number of images successfully compressed in this run. */
    const val KEY_DONE = WorkStatus.KEY_DONE

    /** Progress key: total images queued for this run. */
    const val KEY_TOTAL = WorkStatus.KEY_TOTAL

    /** Progress / output key: number of images that were already small and skipped. */
    const val KEY_SKIPPED = WorkStatus.KEY_SKIPPED

    /** Progress / output key: number of images that failed during this run. */
    const val KEY_FAILED = WorkStatus.KEY_FAILED
}

/**
 * Lets `ClosetApp` schedule background image compression and `SettingsViewModel` trigger an
 * immediate run without a direct dependency on the worker.
 *
 * Android binds a WorkManager implementation in `androidDataModule`; JVM and iOS bind
 * [NoOpImageCompressionScheduler].
 */
interface ImageCompressionScheduler {
    /**
     * Schedules a one-time compression run with idle + battery-not-low constraints and a
     * 30-second initial delay. Repeated calls on app start are no-ops if the work is already
     * pending or running.
     */
    fun schedule()

    /**
     * Starts an immediate one-time run with no constraints, using
     * [ImageCompressionWork.IMMEDIATE_NAME]. Rapid successive calls cancel any in-flight run
     * and start fresh.
     */
    fun runNow()

    /** Live status of the user-triggered immediate run ([ImageCompressionWork.IMMEDIATE_NAME]); null when none. */
    val workStatus: Flow<WorkStatus?>
}
