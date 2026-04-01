package com.closet.core.data.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Shared ONNX encoder that converts any text string into a 384-dimensional
 * L2-normalised float vector using the bundled `snowflake-arctic-embed-xs` INT8 model.
 *
 * This class is used by two callers with different lifecycles:
 * - [com.closet.core.data.worker.EmbeddingWorker] — batch-embeds all wardrobe items in the
 *   background (charging + idle). Delegates the per-text encode call here rather than
 *   duplicating the ONNX inference logic.
 * - `ChatRepository` (Phase 4) — embeds the user's natural-language chat query at query
 *   time (< 50 ms on a flagship NPU) so that the result can be cosine-searched against the
 *   in-memory [com.closet.core.data.util.EmbeddingIndex].
 *
 * **Lazy initialisation.** The ONNX session (~23 MB model) and the WordPiece tokenizer
 * (~250 KB vocabulary) are loaded on the first [encode] call, not at injection time.
 * Subsequent calls reuse the same session — `OrtSession` is thread-safe for concurrent
 * inference runs on the same session object.
 *
 * **Asset requirements** — both must be present in `core/data/src/main/assets/`:
 * - `models/arctic-embed-xs-q8.onnx`
 * - `models/vocab.txt`
 *
 * If either asset is absent, [isAvailable] returns `false` and [encode] returns
 * [Result.failure] — callers must handle gracefully. The worker skips items; the
 * chat ViewModel shows an error state.
 */
@Singleton
class EmbeddingEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "EmbeddingEncoder"

        @VisibleForTesting internal const val MODEL_ASSET = "models/arctic-embed-xs-q8.onnx"
        @VisibleForTesting internal const val VOCAB_ASSET  = "models/vocab.txt"

        /** Output dimension of `snowflake-arctic-embed-xs`. */
        const val HIDDEN_SIZE = 384

        /** Max sequence length fed to the model — texts are truncated beyond this. */
        const val MAX_SEQ_LEN = 128
    }

    // Lazy so that injection doesn't block the main thread on app start.
    // `OrtEnvironment.getEnvironment()` is a process-wide singleton managed by ORT.
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val tokenizer: WordPieceTokenizer? by lazy {
        try {
            WordPieceTokenizer(context.assets.open(VOCAB_ASSET))
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Vocab asset not found at %s — add vocab.txt to assets/models/", VOCAB_ASSET)
            null
        }
    }

    private val session: OrtSession? by lazy {
        try {
            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            OrtSession.SessionOptions().use { options ->
                env.createSession(modelBytes, options)
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Model asset not found at %s — add the ONNX file to assets/models/", MODEL_ASSET)
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load ONNX session from %s", MODEL_ASSET)
            null
        }
    }

    /**
     * `true` if both the ONNX model and the WordPiece vocabulary loaded successfully.
     *
     * Accessing this property triggers lazy initialisation of both assets on the calling thread.
     * Call only from a background dispatcher.
     */
    val isAvailable: Boolean get() = tokenizer != null && session != null

    /**
     * Encodes [text] into a 384-dimensional L2-normalised float vector.
     *
     * Runs on [Dispatchers.IO]. Returns [Result.failure] if the model or vocabulary is
     * unavailable, or if ONNX inference throws. Never throws directly — all exceptions
     * are wrapped in the returned [Result].
     *
     * @param text The input string. May be any length; content beyond [MAX_SEQ_LEN] tokens
     *             is truncated. Typical input is a [com.closet.core.data.util.ItemVectorizer]
     *             prose description, optionally concatenated with an image caption.
     */
    suspend fun encode(text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        val tok  = tokenizer ?: return@withContext Result.failure(
            IllegalStateException("Vocabulary not available — check that $VOCAB_ASSET exists in assets/models/")
        )
        val sess = session  ?: return@withContext Result.failure(
            IllegalStateException("ONNX model not available — check that $MODEL_ASSET exists in assets/models/")
        )
        runCatching {
            val tokens = tok.encode(text, MAX_SEQ_LEN)
            runInference(sess, tokens)
        }
    }

    // ─── Inference ────────────────────────────────────────────────────────────

    /**
     * Runs one ONNX inference pass and returns an L2-normalised 384-float embedding.
     *
     * Inputs: `input_ids`, `attention_mask`, `token_type_ids` — all `Long[1][seqLen]`.
     * Output: `last_hidden_state` `Float[1][seqLen][384]` → mean-pool → L2-normalise.
     */
    private fun runInference(session: OrtSession, tokens: TokenizerOutput): FloatArray {
        val seqLen = tokens.inputIds.size.toLong()
        val shape  = longArrayOf(1L, seqLen)

        OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.inputIds), shape).use { inputIdsTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.attentionMask), shape).use { maskTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.tokenTypeIds), shape).use { typesTensor ->
                    val inputs = mapOf(
                        "input_ids"      to inputIdsTensor,
                        "attention_mask" to maskTensor,
                        "token_type_ids" to typesTensor,
                    )
                    session.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = (result[0].value as Array<Array<FloatArray>>)[0] // [seqLen][384]
                        return l2Normalize(meanPool(hidden, tokens.attentionMask))
                    }
                }
            }
        }
    }

    /**
     * Mean-pools token embeddings over non-padding positions (attention mask == 1).
     */
    private fun meanPool(hidden: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val sum   = FloatArray(HIDDEN_SIZE)
        var count = 0
        for (i in hidden.indices) {
            if (attentionMask[i] == 1L) {
                val tok = hidden[i]
                for (j in 0 until HIDDEN_SIZE) sum[j] += tok[j]
                count++
            }
        }
        if (count > 0) for (j in sum.indices) sum[j] /= count
        return sum
    }

    /**
     * Divides [vector] by its L2 norm, producing a unit vector for cosine similarity
     * (cosine sim of two unit vectors = their dot product).
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        return if (norm > 1e-12f) FloatArray(vector.size) { vector[it] / norm } else vector
    }
}
