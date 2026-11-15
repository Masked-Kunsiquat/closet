package com.closet.core.data.worker

import androidx.work.WorkInfo

/**
 * Maps a WorkManager [WorkInfo] to the platform-neutral [WorkStatus]. Counts are read from the
 * progress data while the work is unfinished and from the output data once it has finished;
 * absent keys read as 0.
 */
fun WorkInfo.toWorkStatus(): WorkStatus {
    val data = if (state.isFinished) outputData else progress
    return WorkStatus(
        id = id.toString(),
        state = when (state) {
            WorkInfo.State.ENQUEUED -> WorkState.ENQUEUED
            WorkInfo.State.RUNNING -> WorkState.RUNNING
            WorkInfo.State.SUCCEEDED -> WorkState.SUCCEEDED
            WorkInfo.State.FAILED -> WorkState.FAILED
            WorkInfo.State.BLOCKED -> WorkState.BLOCKED
            WorkInfo.State.CANCELLED -> WorkState.CANCELLED
        },
        done = data.getInt(WorkStatus.KEY_DONE, 0),
        total = data.getInt(WorkStatus.KEY_TOTAL, 0),
        failed = data.getInt(WorkStatus.KEY_FAILED, 0),
        skipped = data.getInt(WorkStatus.KEY_SKIPPED, 0),
    )
}
