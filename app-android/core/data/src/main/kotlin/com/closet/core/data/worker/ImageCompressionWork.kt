package com.closet.core.data.worker

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between [ImageCompressionWorker] and any observer so neither module needs
 * to depend on the other.
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
    const val KEY_DONE = "done"

    /** Progress key: total images queued for this run. */
    const val KEY_TOTAL = "total"

    /** Progress / output key: number of images that were already small and skipped. */
    const val KEY_SKIPPED = "skipped"

    /** Progress / output key: number of images that failed during this run. */
    const val KEY_FAILED = "failed"
}

/**
 * Abstraction that lets [com.closet.ClosetApp] schedule background image compression and
 * [SettingsViewModel] trigger an immediate run without a direct dependency on
 * [ImageCompressionWorker]. Bound to its concrete implementation by
 * [com.closet.core.data.di.DataModule].
 */
interface ImageCompressionScheduler {
    /**
     * Enqueues a one-time compression run with idle + battery-not-low constraints and a
     * 30-second initial delay. Uses [androidx.work.ExistingWorkPolicy.KEEP] so repeated calls
     * on app start are no-ops if the work is already pending or running.
     */
    fun schedule()

    /**
     * Enqueues an immediate one-time run with no constraints.
     * Uses [ImageCompressionWork.IMMEDIATE_NAME] and [androidx.work.ExistingWorkPolicy.REPLACE]
     * so rapid successive calls cancel any in-flight run and start fresh.
     */
    fun runNow()

    /** Live [WorkInfo] for the user-triggered immediate run ([ImageCompressionWork.IMMEDIATE_NAME]). */
    val workInfo: Flow<WorkInfo?>
}
