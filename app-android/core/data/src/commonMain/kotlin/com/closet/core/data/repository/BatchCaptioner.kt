package com.closet.core.data.repository

import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import kotlinx.coroutines.flow.StateFlow

/**
 * The batch half of image-caption enrichment: platform-neutral so common code (settings) can
 * start a run and observe it. Android's `CaptionEnrichmentProvider` extends it; JVM and iOS bind
 * [NoOpBatchCaptioner].
 */
interface BatchCaptioner {

    /** `true` in full builds; `false` in FOSS builds and on platforms without captioning. Gate all caption paths on this. */
    val isSupported: Boolean

    /**
     * In-flight progress for the current batch enrichment run.
     * `null` when idle; non-null while [startBatchEnrichment] is executing.
     */
    val progress: StateFlow<BatchCaptionProgress?>

    /**
     * Terminal result of the most recent batch enrichment run.
     * Set when the run finishes; cleared by [consumeResult].
     */
    val result: StateFlow<BatchCaptionResult?>

    /**
     * Launches a batch caption enrichment pass over all items that have an image but
     * no caption yet. Updates [progress] throughout and sets [result] on completion.
     * No-op if a run is already in progress.
     */
    fun startBatchEnrichment()

    /**
     * Signals that the UI has consumed the most recent batch-caption result.
     * Clears [result] so the same summary is not shown again after dismissal.
     */
    fun consumeResult()
}
