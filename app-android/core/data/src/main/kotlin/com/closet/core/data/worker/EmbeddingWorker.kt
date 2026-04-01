package com.closet.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.EmbeddingDao
import com.closet.core.data.model.ItemEmbeddingEntity
import com.closet.core.data.util.EmbeddingEncoder
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

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
 * 2. Delegate encode call to [EmbeddingEncoder] → 384-float unit vector.
 * 3. Serialise as little-endian float32 BLOB → upsert into `item_embeddings`.
 *
 * ONNX inference and tokenisation are handled entirely by [EmbeddingEncoder]. If the
 * encoder is unavailable (model or vocab asset missing), the worker returns [Result.failure]
 * immediately so WorkManager stops retrying until the next periodic interval.
 */
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val embeddingDao: EmbeddingDao,
    private val encoder: EmbeddingEncoder,
) : CoroutineWorker(context, params) {

    companion object {
        val WORK_NAME = EmbeddingWork.NAME
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // ── 1. Guard: encoder must be available (model + vocab assets present) ─
        if (!encoder.isAvailable) {
            Timber.e("EmbeddingWorker: encoder not available — model or vocab asset missing")
            return@withContext Result.failure(workDataOf("error" to "encoder_not_available"))
        }

        // ── 2. Determine work queue ──────────────────────────────────────────
        val itemIds = embeddingDao.getItemIdsNeedingEmbedding(MODEL_VERSION)
        val total   = itemIds.size
        if (total == 0) {
            Timber.d("EmbeddingWorker: nothing to embed")
            return@withContext Result.success(workDataOf(KEY_DONE to 0, KEY_TOTAL to 0, KEY_FAILED to 0))
        }

        val items = embeddingDao.getTextsForEmbedding(itemIds)

        // ── 3. Embed each item ───────────────────────────────────────────────
        var done   = 0
        var failed = 0

        for (item in items) {
            try {
                val text = buildString {
                    append(item.semanticDescription)
                    item.imageCaption?.let { append(' ').append(it) }
                }

                val vector = encoder.encode(text).getOrThrow()

                embeddingDao.upsert(
                    ItemEmbeddingEntity(
                        itemId        = item.id,
                        embeddingBlob = floatsToBlob(vector),
                        modelVersion  = MODEL_VERSION,
                        inputSnapshot = text,
                        embeddedAt    = Instant.now(),
                    )
                )
                done++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "EmbeddingWorker: failed to embed item %d", item.id)
                failed++
            }

            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
        }

        Timber.d("EmbeddingWorker: done=%d failed=%d total=%d", done, failed, total)
        Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_FAILED to failed))
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
