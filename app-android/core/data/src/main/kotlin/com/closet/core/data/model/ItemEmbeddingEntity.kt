package com.closet.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Stores a pre-computed embedding vector for a clothing item.
 *
 * Vectors are stored as raw float32 little-endian bytes (384 dimensions for
 * `snowflake-arctic-embed-xs`). [modelVersion] triggers re-embedding when the ONNX
 * model changes; [inputSnapshot] triggers re-embedding when the item's text changes.
 *
 * Cascade-deletes when the parent [ClothingItemEntity] is removed so orphan
 * vectors never accumulate.
 *
 * Populated by `EmbeddingWorker` (Phase 3). This entity is the Phase 2A storage
 * layer — a plain Room BLOB column that is sufficient until ~2,000 items, at which
 * point Phase 2B (sqlite-vss) can take over.
 */
@Entity(
    tableName = "item_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("item_id", unique = true)],
)
data class ItemEmbeddingEntity(
    /** Foreign key to [ClothingItemEntity.id] — also serves as the primary key. */
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: Long,

    /**
     * Serialised float32 vector in little-endian byte order.
     * Size: `dimensions × 4` bytes (e.g. 384 × 4 = 1 536 bytes for MiniLM-L6-v2).
     *
     * Encode with:
     * ```kotlin
     * ByteBuffer.allocate(floats.size * 4)
     *     .order(ByteOrder.LITTLE_ENDIAN)
     *     .apply { asFloatBuffer().put(floats) }
     *     .array()
     * ```
     */
    @ColumnInfo(name = "embedding_blob")
    val embeddingBlob: ByteArray,

    /**
     * Identifies which model produced this vector (e.g. `"minilm-l6-v2-q8-v1"`).
     * The embedding worker compares this against its current [EmbeddingWork.MODEL_VERSION]
     * to detect stale embeddings that need to be re-computed.
     */
    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    /**
     * The exact text string fed to the model (`semanticDescription + " " + imageCaption`).
     * Stored so [EmbeddingDao.getItemIdsNeedingEmbedding] can detect stale embeddings via
     * a SQL comparison without needing a separate hash function in the database.
     *
     * Null only on rows migrated from v5 before this column was added; the worker treats
     * null as stale and re-embeds.
     */
    @ColumnInfo(name = "input_snapshot")
    val inputSnapshot: String?,

    /** Timestamp of when this embedding was last computed. */
    @ColumnInfo(name = "embedded_at")
    val embeddedAt: Instant,
)
