package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 4 → 5: Vector storage for RAG pipeline (Phase 2A).
 *
 * Changes:
 * - Creates `item_embeddings` table to store pre-computed float32 embedding vectors
 *   alongside the Room database. Each row holds a BLOB of `dimensions × 4` bytes
 *   (384 dims for all-MiniLM-L6-v2 = 1 536 bytes), the model version string used to
 *   detect stale embeddings, and the timestamp of last embedding.
 * - Cascade-deletes on `clothing_items` removal to prevent orphan vectors.
 *
 * Storage estimate: 300 items × 1 536 B ≈ 460 KB — fits comfortably in memory for
 * Phase 2A cosine-similarity search. Phase 2B (sqlite-vss) can replace this when
 * the wardrobe exceeds ~2,000 items.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Partial index — always drop first (AGENTS.md) ──────────────────────
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // ── item_embeddings ─────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `item_embeddings` (
                `item_id` INTEGER NOT NULL,
                `embedding_blob` BLOB NOT NULL,
                `model_version` TEXT NOT NULL,
                `embedded_at` TEXT NOT NULL,
                PRIMARY KEY(`item_id`),
                FOREIGN KEY(`item_id`) REFERENCES `clothing_items`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_item_embeddings_item_id` " +
            "ON `item_embeddings` (`item_id`)"
        )
    }
}
