package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 → 2: Phase 5 schema additions (Gaps 4 and 5).
 *
 * Changes:
 * - seasons: add temp_low_c / temp_high_c (°C) for temperature-band season matching.
 *   Backfills sensible global defaults; All Season is left NULL (always applicable).
 * - categories: add warmth_layer TEXT NOT NULL DEFAULT 'None'.
 *   Values: None | Base | Mid | Outer. Backfills 'Outer' for Outerwear.
 * - outfit_logs: add precipitation_mm / wind_speed_kmh for recommendation engine
 *   data collection. Auto-populated on log creation via Phase 4.2 forecast hook.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Partial index — always drop first (AGENTS.md) ──────────────────────
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // ── Gap 4: Temperature bands on seasons ────────────────────────────────
        db.execSQL("ALTER TABLE seasons ADD COLUMN temp_low_c REAL")
        db.execSQL("ALTER TABLE seasons ADD COLUMN temp_high_c REAL")

        // Backfill defaults. All Season left NULL — it applies at any temperature.
        db.execSQL("UPDATE seasons SET temp_low_c = 10.0,  temp_high_c = 20.0 WHERE name = 'Spring'")
        db.execSQL("UPDATE seasons SET temp_low_c = 22.0,  temp_high_c = 35.0 WHERE name = 'Summer'")
        db.execSQL("UPDATE seasons SET temp_low_c = 5.0,   temp_high_c = 18.0 WHERE name = 'Fall'")
        db.execSQL("UPDATE seasons SET temp_low_c = -10.0, temp_high_c = 5.0  WHERE name = 'Winter'")

        // ── Gap 4: Warmth layer on categories ──────────────────────────────────
        db.execSQL("ALTER TABLE categories ADD COLUMN warmth_layer TEXT NOT NULL DEFAULT 'None'")

        // Backfill known warmth layers. Other categories default to 'None'.
        // Users can update Mid/Base assignments via the UI when the engine ships.
        db.execSQL("UPDATE categories SET warmth_layer = 'Outer' WHERE name = 'Outerwear'")
        db.execSQL("UPDATE categories SET warmth_layer = 'Base'  WHERE name = 'Underwear & Intimates'")

        // ── Gap 5: Precipitation and wind on outfit_logs ───────────────────────
        db.execSQL("ALTER TABLE outfit_logs ADD COLUMN precipitation_mm REAL")
        db.execSQL("ALTER TABLE outfit_logs ADD COLUMN wind_speed_kmh REAL")
    }
}
