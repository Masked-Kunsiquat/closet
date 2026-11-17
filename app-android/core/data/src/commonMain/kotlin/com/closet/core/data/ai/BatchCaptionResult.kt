package com.closet.core.data.ai

import com.closet.core.data.repository.BatchCaptioner
import kotlin.uuid.Uuid

/** Terminal result emitted on [BatchCaptioner.result] once a batch enrichment run finishes. */
data class BatchCaptionResult(
    val done: Int,
    val failed: Int,
    val resultId: Uuid = Uuid.random(),
    /** Set when the batch stopped early (e.g. the OS blocked on-device AI); shown to the user as-is. */
    val stoppedReason: String? = null,
)
