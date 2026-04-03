package com.closet.backup

import android.net.Uri

/**
 * Represents the lifecycle of a single backup export or restore operation.
 *
 * Emitted by [BackupForegroundService.progress] and observed by `BackupViewModel`.
 */
sealed interface BackupProgress {
    /** No operation in progress. Initial / post-completion state. */
    data object Idle : BackupProgress

    /**
     * Operation is actively running.
     *
     * @param step Human-readable label for the current stage (e.g. "Copying images").
     * @param done Number of units completed so far.
     * @param total Total units in this stage (0 if unknown).
     */
    data class Running(val step: String, val done: Int, val total: Int) : BackupProgress

    /**
     * Operation completed successfully.
     *
     * @param outputUri URI of the written `.hangr` file (export only; `null` for restore).
     */
    data class Success(val outputUri: Uri? = null) : BackupProgress

    /** Operation failed with a user-facing [message]. */
    data class Error(val message: String) : BackupProgress
}
