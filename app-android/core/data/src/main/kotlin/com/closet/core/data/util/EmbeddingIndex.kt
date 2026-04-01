package com.closet.core.data.util

import com.closet.core.data.dao.EmbeddingDao
import com.closet.core.data.model.ItemEmbeddingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cosine-similarity index over all stored item embeddings.
 *
 * Loads the full `item_embeddings` table into a `List<Pair<Long, FloatArray>>` once
 * at app start (via [ClosetApp.onCreate] → [load]) and holds it for the lifetime of
 * the process. At 300 items × 384 dims × 4 bytes ≈ 460 KB this is negligible; the
 * approach remains appropriate up to ~2,000 items (Phase 2B sqlite-vss threshold).
 *
 * **Thread safety.** The [index] reference is `@Volatile`, so a [reload] replacing the
 * list is immediately visible to any thread calling [search]. Callers that read the old
 * list during a reload get a consistent (if slightly stale) snapshot — acceptable given
 * that embeddings change only when `EmbeddingWorker` completes a background run.
 *
 * **Loading.** Call [load] once at startup from an IO-scoped coroutine. Call [reload]
 * after `EmbeddingWorker` finishes to pick up newly embedded items without restarting
 * the app. Neither call blocks the main thread.
 *
 * **Searching.** [search] is a synchronous hot-path call (< 1 ms for 300 items on a
 * modern ARM CPU). Vectors are already L2-normalised by [EmbeddingEncoder], so cosine
 * similarity reduces to a plain dot product.
 */
@Singleton
class EmbeddingIndex @Inject constructor(
    private val embeddingDao: EmbeddingDao,
) {

    @Volatile private var index: List<Pair<Long, FloatArray>> = emptyList()

    /** Number of items currently in the index. Useful for logging and empty-state guards. */
    val size: Int get() = index.size

    /** `true` once [load] has completed at least one successful DB read. */
    val isLoaded: Boolean get() = index.isNotEmpty()

    /**
     * Loads all stored embeddings from the database into memory.
     *
     * Runs on [Dispatchers.IO]. Safe to call multiple times — each call fully replaces
     * the in-memory index with the current DB state.
     *
     * **Where to call:**
     * - `ClosetApp.onCreate()` (background scope) so the index is warm before the user
     *   opens the chat screen for the first time.
     * - After `EmbeddingWorker` completes a run, via [reload], so newly embedded items
     *   are immediately searchable.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        val rows = embeddingDao.getAll()
        index = rows.map { entity -> entity.itemId to entity.toFloatArray() }
        Timber.d("EmbeddingIndex: loaded %d vectors", index.size)
    }

    /**
     * Drops the current index and reloads from the database.
     *
     * Semantically identical to [load]; provided as a named alias to signal intent at
     * call sites where the index already exists and is being refreshed.
     */
    suspend fun reload() = load()

    /**
     * Returns the item IDs of the [topK] most similar items to [queryVec], ranked by
     * cosine similarity (highest first).
     *
     * Returns an empty list if the index has not been loaded yet — callers (e.g.
     * `ChatRepository`) should check [isLoaded] and surface an appropriate error state
     * rather than silently returning no results.
     *
     * @param queryVec  L2-normalised 384-float query vector from [EmbeddingEncoder.encode].
     * @param topK      Maximum number of results to return. Defaults to 5.
     */
    fun search(queryVec: FloatArray, topK: Int = 5): List<Long> {
        val snapshot = index          // read volatile ref once; consistent for this call
        if (snapshot.isEmpty()) return emptyList()
        return snapshot
            .map { (id, vec) -> id to dot(queryVec, vec) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    /**
     * Dot product of two float arrays.
     *
     * Both [a] and [b] are expected to be L2-normalised unit vectors (guaranteed by
     * [EmbeddingEncoder.l2Normalize]), so their dot product equals their cosine similarity.
     */
    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}

// ─── Extension ────────────────────────────────────────────────────────────────

/**
 * Decodes the little-endian float32 [ItemEmbeddingEntity.embeddingBlob] into a
 * [FloatArray]. Inverse of the encode step in `EmbeddingWorker.floatsToBlob`.
 */
private fun ItemEmbeddingEntity.toFloatArray(): FloatArray =
    ByteBuffer.wrap(embeddingBlob)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asFloatBuffer()
        .let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
