package com.closet.core.data.ai

import java.util.UUID

/** Terminal result emitted on [CaptionEnrichmentProvider.result] once a batch enrichment run finishes. */
data class BatchCaptionResult(
    val done: Int,
    val failed: Int,
    val resultId: UUID = UUID.randomUUID(),
    /** Set when the batch stopped early (e.g. the OS blocked on-device AI); shown to the user as-is. */
    val stoppedReason: String? = null,
)
