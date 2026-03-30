package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5 → 6: Input-snapshot column for embedding staleness detection (Phase 3).
 *
 * Changes:
 * - item_embeddings: add `input_snapshot TEXT` — the exact text string
 *   (`semantic_description + " " + image_caption`) that was fed to the ONNX model.
 *   Stored so [EmbeddingWorker] can detect stale embeddings via a SQL comparison
 *   without requiring a hash function in SQLite.
 *
 * Existing rows receive `input_snapshot = NULL`.  [EmbeddingDao.getItemIdsNeedingEmbedding]
 * treats NULL as stale, so all existing embeddings are transparently re-computed on the
 * next worker run after this migration.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Partial index — always drop first (AGENTS.md) ──────────────────────
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // ── item_embeddings.input_snapshot ──────────────────────────────────────
        // No DEFAULT — NULL indicates "was never stored" and is treated as stale.
        db.execSQL("ALTER TABLE item_embeddings ADD COLUMN input_snapshot TEXT")
    }
}
