package com.closet.core.data.worker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.EmbeddingDao
import com.closet.core.data.model.ItemEmbeddingEntity
import com.closet.core.data.util.TokenizerOutput
import com.closet.core.data.util.WordPieceTokenizer
import com.closet.core.data.worker.EmbeddingWork.KEY_DONE
import com.closet.core.data.worker.EmbeddingWork.KEY_FAILED
import com.closet.core.data.worker.EmbeddingWork.KEY_TOTAL
import com.closet.core.data.worker.EmbeddingWork.MODEL_VERSION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import java.time.Instant
import kotlin.math.sqrt

/**
 * Background worker that embeds every clothing item whose `semantic_description` is populated
 * but has no up-to-date vector in `item_embeddings`.
 *
 * Runs only when the device is charging and idle (WorkManager constraints) so it never
 * competes with foreground work. Scheduled as unique periodic work (1-hour interval) by
 * [EmbeddingScheduler]; re-runs automatically as new items are saved.
 *
 * Per-item pipeline:
 * 1. Concatenate `semanticDescription + " " + imageCaption` (caption optional).
 * 2. Tokenise with [WordPieceTokenizer] (BERT WordPiece, `vocab.txt` from assets).
 * 3. Run `snowflake-arctic-embed-xs` INT8 ONNX session → `last_hidden_state` [1, seq, 384].
 * 4. Mean-pool over non-padding tokens → L2-normalise → 384-float unit vector.
 * 5. Serialise as little-endian float32 BLOB → upsert into `item_embeddings`.
 *
 * **Asset requirements** (must be present before the worker produces results):
 * - `assets/models/arctic-embed-xs-q8.onnx`  (~23 MB, INT8 quantized)
 * - `assets/models/vocab.txt`                (~250 KB, BERT uncased vocabulary)
 *
 * If either asset is missing the worker returns [Result.failure] with an `"error"` key
 * so WorkManager stops retrying until the next periodic interval.
 */
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val embeddingDao: EmbeddingDao,
) : CoroutineWorker(context, params) {

    companion object {
        val WORK_NAME = EmbeddingWork.NAME

        private const val MODEL_ASSET = "models/arctic-embed-xs-q8.onnx"
        private const val VOCAB_ASSET  = "models/vocab.txt"

        /** Embedding dimension of `all-MiniLM-L6-v2`. */
        private const val HIDDEN_SIZE = 384

        /** Sequence length fed to the model — longer texts are truncated. */
        private const val MAX_SEQ_LEN = 128
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // ── 1. Load assets ───────────────────────────────────────────────────
        val modelBytes = try {
            context.assets.open(MODEL_ASSET).use { it.readBytes() }
        } catch (e: IOException) {
            Timber.e(e, "EmbeddingWorker: model asset not found at $MODEL_ASSET — add the ONNX file to assets/models/")
            return@withContext Result.failure(workDataOf("error" to "model_not_found"))
        }

        val tokenizer = try {
            WordPieceTokenizer(context.assets.open(VOCAB_ASSET))
        } catch (e: IOException) {
            Timber.e(e, "EmbeddingWorker: vocab asset not found at $VOCAB_ASSET — add vocab.txt to assets/models/")
            return@withContext Result.failure(workDataOf("error" to "vocab_not_found"))
        }

        // ── 2. Determine work queue ──────────────────────────────────────────
        val itemIds = embeddingDao.getItemIdsNeedingEmbedding(MODEL_VERSION)
        val total   = itemIds.size
        if (total == 0) {
            Timber.d("EmbeddingWorker: nothing to embed")
            return@withContext Result.success(workDataOf(KEY_DONE to 0, KEY_FAILED to 0))
        }

        val items = embeddingDao.getTextsForEmbedding(itemIds)

        // ── 3. Open ONNX session and embed ───────────────────────────────────
        var done   = 0
        var failed = 0

        val env = OrtEnvironment.getEnvironment()
        try {
            env.createSession(modelBytes, OrtSession.SessionOptions()).use { session ->
                for (item in items) {
                    try {
                        val text = buildString {
                            append(item.semanticDescription)
                            item.imageCaption?.let { append(' ').append(it) }
                        }

                        val tokens = tokenizer.encode(text, MAX_SEQ_LEN)
                        val vector = embed(env, session, tokens)

                        embeddingDao.upsert(
                            ItemEmbeddingEntity(
                                itemId       = item.id,
                                embeddingBlob = floatsToBlob(vector),
                                modelVersion  = MODEL_VERSION,
                                embeddedAt    = Instant.now(),
                            )
                        )
                        done++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "EmbeddingWorker: failed to embed item ${item.id}")
                        failed++
                        done++
                    }

                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OrtException) {
            Timber.e(e, "EmbeddingWorker: ONNX session error")
            return@withContext Result.failure(workDataOf("error" to "onnx_session_failed"))
        }

        Timber.d("EmbeddingWorker: done=$done failed=$failed total=$total")
        return@withContext Result.success(workDataOf(KEY_DONE to done, KEY_FAILED to failed))
    }

    // ─── Inference helpers ────────────────────────────────────────────────────

    /**
     * Runs one ONNX inference pass and returns an L2-normalised 384-float embedding.
     *
     * Inputs: `input_ids`, `attention_mask`, `token_type_ids` — all Long[1][seqLen].
     * Output: `last_hidden_state` Float[1][seqLen][384] → mean-pool → L2-normalise.
     */
    private fun embed(
        env: OrtEnvironment,
        session: OrtSession,
        tokens: TokenizerOutput,
    ): FloatArray {
        val seqLen = tokens.inputIds.size.toLong()
        val shape  = longArrayOf(1L, seqLen)

        val inputIdsTensor  = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.inputIds),     shape)
        val maskTensor      = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.attentionMask), shape)
        val typesTensor     = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.tokenTypeIds),  shape)

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

    /**
     * Mean-pools token embeddings, weighting only real (non-padding) positions
     * (attention mask == 1).
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

    /** Divides [vector] by its L2 norm, producing a unit vector for cosine similarity. */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        return if (norm > 1e-12f) FloatArray(vector.size) { vector[it] / norm } else vector
    }

    /**
     * Serialises [floats] as a little-endian float32 byte array for BLOB storage.
     *
     * Decode with:
     * ```kotlin
     * ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
     *     .let { FloatArray(it.remaining()).also { arr -> it.get(arr) } }
     * ```
     */
    private fun floatsToBlob(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { asFloatBuffer().put(floats) }
            .array()
}
