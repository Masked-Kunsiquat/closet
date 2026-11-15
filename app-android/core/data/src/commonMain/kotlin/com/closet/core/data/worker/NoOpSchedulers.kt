package com.closet.core.data.worker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Schedulers for platforms with no background work runner yet (JVM, iOS). Every call does
// nothing and the status is always null, so the UI never shows a run that didn't happen.

/** An [EmbeddingScheduler] that never schedules anything. */
object NoOpEmbeddingScheduler : EmbeddingScheduler {
    override fun schedule() = Unit
    override fun cancel() = Unit
    override fun runNow() = Unit
    override val workStatus: Flow<WorkStatus?> = flowOf(null)
}

/** An [ImageCompressionScheduler] that never schedules anything. */
object NoOpImageCompressionScheduler : ImageCompressionScheduler {
    override fun schedule() = Unit
    override fun runNow() = Unit
    override val workStatus: Flow<WorkStatus?> = flowOf(null)
}

/** A [BatchSegmentationScheduler] that reports segmentation unsupported and never schedules. */
object NoOpBatchSegmentationScheduler : BatchSegmentationScheduler {
    override val isSupported: Boolean = false
    override fun schedule() = Unit
    override val workStatus: Flow<WorkStatus?> = flowOf(null)
}
