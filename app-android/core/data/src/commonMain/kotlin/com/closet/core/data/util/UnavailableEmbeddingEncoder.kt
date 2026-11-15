package com.closet.core.data.util

import com.closet.core.data.worker.EmbeddingWork

/**
 * An [EmbeddingEncoder] for platforms with no on-device embedding model yet. It is never
 * available and [encode] always fails, so the embedding work does nothing and chat reports
 * that search is unavailable. [modelVersion] is the Android model's, so stored vectors are
 * not treated as stale.
 */
object UnavailableEmbeddingEncoder : EmbeddingEncoder {
    override val isAvailable: Boolean = false

    override val modelVersion: String = EmbeddingWork.MODEL_VERSION

    override suspend fun encode(text: String): Result<FloatArray> =
        Result.failure(UnsupportedOperationException("On-device embeddings are not available on this platform"))
}
