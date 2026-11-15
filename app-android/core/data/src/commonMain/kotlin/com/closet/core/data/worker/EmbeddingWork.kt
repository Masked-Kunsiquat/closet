package com.closet.core.data.worker

import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between the embedding worker and any observer (e.g. the Settings screen)
 * so neither module needs to depend on the other.
 */
object EmbeddingWork {
    /** Unique work name for the periodic background run. */
    const val NAME = "embedding_worker"

    /**
     * Unique work name for user-triggered one-time embedding runs (no charging/idle constraints).
     * Separate from [NAME] so it doesn't cancel the periodic schedule.
     */
    const val IMMEDIATE_NAME = "embedding_worker_immediate"

    /**
     * Identifies the model that produced an embedding; the Android encoder's
     * `EmbeddingEncoder.modelVersion`. Changing this value causes the embedding worker to
     * re-embed all items on the next run.
     */
    const val MODEL_VERSION = "arctic-embed-xs-q8-v1"

    /** Progress / output key: number of items successfully embedded in this run. */
    const val KEY_DONE = WorkStatus.KEY_DONE

    /** Progress key: total items queued for this run. */
    const val KEY_TOTAL = WorkStatus.KEY_TOTAL

    /** Progress / output key: number of items that failed during this run. */
    const val KEY_FAILED = WorkStatus.KEY_FAILED
}

/**
 * Lets callers (`ClosetApp`, `SettingsViewModel`) trigger or cancel background embedding
 * without a direct dependency on the worker.
 *
 * Android binds a WorkManager implementation in `androidDataModule`; JVM and iOS bind
 * [NoOpEmbeddingScheduler].
 */
interface EmbeddingScheduler {
    /**
     * Schedules the periodic embedding run if not already scheduled.
     * Safe to call on every app start: duplicate schedules are no-ops.
     */
    fun schedule()

    /** Cancels any pending or running periodic embedding work. */
    fun cancel()

    /**
     * Starts a one-time embedding run immediately, without charging/idle constraints.
     * Uses [EmbeddingWork.IMMEDIATE_NAME] so it doesn't interfere with the periodic schedule.
     *
     * **Not idempotent.** Calling [runNow] while an immediate run is already in flight cancels
     * that run and starts a new one. Avoid rapid successive calls.
     */
    fun runNow()

    /** Live status of the immediate one-time run ([EmbeddingWork.IMMEDIATE_NAME]); null when none. */
    val workStatus: Flow<WorkStatus?>
}
