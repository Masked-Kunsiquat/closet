package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 3 → 4: Semantic shadow columns for RAG pipeline (Phase 1).
 *
 * Changes:
 * - clothing_items: add semantic_description TEXT — structured prose paragraph assembled
 *   by ItemVectorizer from junction tables. Null = not yet enriched.
 * - clothing_items: add image_caption TEXT — AI-generated one-sentence photo caption from
 *   ML Kit GenAI Image Description API. Null = not yet enriched.
 *
 * These columns are populated incrementally by the background embedding pipeline and the
 * at-capture / batch enrichment flows. They form the input to the embedding pipeline in
 * Phase 3.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Partial index — always drop first (AGENTS.md) ──────────────────────
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // ── clothing_items.semantic_description ────────────────────────────────
        // No DEFAULT — existing rows stay NULL (null = not yet enriched).
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN semantic_description TEXT")

        // ── clothing_items.image_caption ───────────────────────────────────────
        db.execSQL("ALTER TABLE clothing_items ADD COLUMN image_caption TEXT")
    }
}
