package com.closet.core.data.util

/**
 * Converts text into a [HIDDEN_SIZE]-dimensional L2-normalised float vector, used by the
 * embedding worker (batch item embedding) and by chat (query embedding), whose vectors are
 * compared in [EmbeddingIndex].
 *
 * Each platform binds its own implementation in its data module: Android binds
 * `OnnxEmbeddingEncoder`; JVM and iOS bind [UnavailableEmbeddingEncoder] for now.
 */
interface EmbeddingEncoder {
    /**
     * `true` if the encoder can produce embeddings. On Android this lazily loads the model,
     * so read it only from a background dispatcher.
     */
    val isAvailable: Boolean

    /**
     * Identifies the model that produces this encoder's vectors; stored with each embedding
     * as `model_version`. Vectors from a different version are re-embedded and ignored by search.
     */
    val modelVersion: String

    /**
     * Encodes [text] into a [HIDDEN_SIZE]-float unit vector. Returns [Result.failure] when the
     * encoder is unavailable or inference fails; never throws (except for cancellation).
     */
    suspend fun encode(text: String): Result<FloatArray>

    companion object {
        /** Output dimension of `snowflake-arctic-embed-xs`. */
        const val HIDDEN_SIZE = 384
    }
}
