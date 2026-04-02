package com.closet.core.data.ai

/** Terminal result emitted on [CaptionEnrichmentProvider.result] once a batch enrichment run finishes. */
data class BatchCaptionResult(val done: Int, val failed: Int)
