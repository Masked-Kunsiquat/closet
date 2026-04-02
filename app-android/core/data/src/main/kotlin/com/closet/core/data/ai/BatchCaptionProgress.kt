package com.closet.core.data.ai

/** In-flight progress for a batch caption enrichment run. `null` on [CaptionEnrichmentProvider.progress] means idle. */
data class BatchCaptionProgress(val done: Int, val total: Int, val failed: Int)
