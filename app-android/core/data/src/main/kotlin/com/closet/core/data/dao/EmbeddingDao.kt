package com.closet.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.closet.core.data.model.ItemEmbeddingEntity

/**
 * DAO for the `item_embeddings` table (Phase 2A vector storage).
 *
 * All writes go through [upsert] — re-embedding an item simply overwrites the
 * previous row. Reads are either full-table (for in-memory cosine search at query
 * time) or filtered by [modelVersion] to find items that need (re-)embedding.
 */
@Dao
interface EmbeddingDao {

    /**
     * Inserts or replaces the embedding for an item.
     * Called by `EmbeddingWorker` after each successful ONNX inference.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: ItemEmbeddingEntity)

    /**
     * Returns all stored embeddings.
     *
     * Loaded once at query time into an in-memory `List<Pair<Long, FloatArray>>`
     * for cosine similarity search. At 300 items × 384 dims × 4 bytes ≈ 460 KB
     * this is negligible; remains appropriate until ~2,000 items (Phase 2B threshold).
     */
    @Query("SELECT * FROM item_embeddings")
    suspend fun getAll(): List<ItemEmbeddingEntity>

    /**
     * Returns the IDs of clothing items that have a `semantic_description` but either
     * have no embedding yet or whose embedding was produced by an older model version.
     *
     * Used by `EmbeddingWorker` to build its work queue before each run.
     *
     * @param modelVersion the current model identifier (e.g. [EmbeddingWork.MODEL_VERSION])
     */
    @Query("""
        SELECT ci.id FROM clothing_items ci
        LEFT JOIN item_embeddings ie ON ci.id = ie.item_id
        WHERE ci.semantic_description IS NOT NULL
          AND (ie.item_id IS NULL OR ie.model_version != :modelVersion)
    """)
    suspend fun getItemIdsNeedingEmbedding(modelVersion: String): List<Long>
}
