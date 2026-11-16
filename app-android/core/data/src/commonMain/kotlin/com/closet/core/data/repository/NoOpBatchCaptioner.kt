package com.closet.core.data.repository

import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [BatchCaptioner] for platforms without captioning (JVM, iOS): unsupported, never runs. */
object NoOpBatchCaptioner : BatchCaptioner {
    override val isSupported: Boolean = false
    override val progress: StateFlow<BatchCaptionProgress?> = MutableStateFlow(null)
    override val result: StateFlow<BatchCaptionResult?> = MutableStateFlow(null)
    override fun startBatchEnrichment() = Unit
    override fun consumeResult() = Unit
}
