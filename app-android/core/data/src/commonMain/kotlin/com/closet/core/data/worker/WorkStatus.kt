package com.closet.core.data.worker

/** The lifecycle state of a background job. Mirrors WorkManager's `WorkInfo.State` one to one. */
enum class WorkState { ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED }

/**
 * A platform-neutral snapshot of a background job, exposed by the scheduler interfaces
 * ([EmbeddingScheduler], [ImageCompressionScheduler], [BatchSegmentationScheduler]).
 *
 * The counts come from the job's progress while it runs and from its output once it has
 * finished; a count the job didn't report is 0. Not every job reports every count.
 *
 * @property id Stable identifier of this run (on Android, `WorkInfo.id.toString()`).
 */
data class WorkStatus(
    val id: String,
    val state: WorkState,
    val done: Int,
    val total: Int,
    val failed: Int,
    val skipped: Int,
) {
    companion object {
        /** Progress / output key: items processed successfully in this run. */
        const val KEY_DONE = "done"

        /** Progress key: total items queued for this run. */
        const val KEY_TOTAL = "total"

        /** Progress / output key: items that failed during this run. */
        const val KEY_FAILED = "failed"

        /** Progress / output key: items skipped during this run. */
        const val KEY_SKIPPED = "skipped"
    }
}
