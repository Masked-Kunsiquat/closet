package com.closet.core.data.worker

/**
 * Constants shared between [EmbeddingWorker] and any observer (e.g. a future Settings screen)
 * so neither module needs to depend on the other.
 */
object EmbeddingWork {
    /** Unique work name for [androidx.work.WorkManager.enqueueUniquePeriodicWork]. */
    const val NAME = "embedding_worker"

    /**
     * Unique work name for user-triggered one-time embedding runs (no charging/idle constraints).
     * Separate from [NAME] so it doesn't cancel the periodic schedule.
     */
    const val IMMEDIATE_NAME = "embedding_worker_immediate"

    /**
     * Identifies the model that produced an embedding.
     * Changing this value causes [EmbeddingWorker] to re-embed all items on the next run.
     */
    const val MODEL_VERSION = "arctic-embed-xs-q8-v1"

    /** Progress / output key: number of items successfully embedded in this run. */
    const val KEY_DONE = "done"

    /** Progress key: total items queued for this run. */
    const val KEY_TOTAL = "total"

    /** Progress / output key: number of items that failed during this run. */
    const val KEY_FAILED = "failed"
}

/**
 * Abstraction that lets future callers (e.g. `SettingsViewModel`) trigger or cancel
 * background embedding without a direct dependency on `EmbeddingWorker`.
 *
 * Bound to [EmbeddingSchedulerImpl] by [com.closet.core.data.di.DataModule].
 */
interface EmbeddingScheduler {
    /**
     * Enqueues the periodic embedding worker if not already scheduled.
     * Safe to call on every app start — uses [androidx.work.ExistingPeriodicWorkPolicy.KEEP]
     * so duplicate enqueues are no-ops.
     */
    fun schedule()

    /** Cancels any pending or running embedding work. */
    fun cancel()

    /**
     * Enqueues a one-time embedding run immediately, without charging/idle constraints.
     * Uses [EmbeddingWork.IMMEDIATE_NAME] so it doesn't interfere with the periodic schedule.
     * Safe to call while the periodic work is scheduled — they run independently.
     */
    fun runNow()
}
