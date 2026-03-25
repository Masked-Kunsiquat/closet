package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 2 → 3: Schema & data foundation for outfit recommendations (Phase 1).
 *
 * Changes:
 * - categories: add outfit_role TEXT NOT NULL DEFAULT 'Other'.
 *   Values: Top | Bottom | OnePiece | Outerwear | Footwear | Accessory | Other.
 *   Used by the category completeness check in the recommendation engine.
 *   Backfills the 6 known roles; all other categories receive 'Other'.
 * - colors: add color_family TEXT NOT NULL DEFAULT 'Neutral'.
 *   Values: Neutral | Earth | Cool | Warm | Bright.
 *   Used by color harmony scoring in the recommendation engine.
 *   Backfills each seeded color by name.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Partial index — always drop first (AGENTS.md) ──────────────────────
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // ── categories.outfit_role ──────────────────────────────────────────────
        db.execSQL("ALTER TABLE categories ADD COLUMN outfit_role TEXT NOT NULL DEFAULT 'Other'")

        // Backfill known roles by name.
        // Bags, Activewear, Underwear & Intimates, Swimwear → 'Other' (default, no update needed).
        db.execSQL("UPDATE categories SET outfit_role = 'Top'       WHERE name = 'Tops'")
        db.execSQL("UPDATE categories SET outfit_role = 'Bottom'    WHERE name = 'Bottoms'")
        db.execSQL("UPDATE categories SET outfit_role = 'Outerwear' WHERE name = 'Outerwear'")
        db.execSQL("UPDATE categories SET outfit_role = 'OnePiece'  WHERE name = 'Dresses & Jumpsuits'")
        db.execSQL("UPDATE categories SET outfit_role = 'Footwear'  WHERE name = 'Footwear'")
        db.execSQL("UPDATE categories SET outfit_role = 'Accessory' WHERE name = 'Accessories'")

        // ── colors.color_family ─────────────────────────────────────────────────
        db.execSQL("ALTER TABLE colors ADD COLUMN color_family TEXT NOT NULL DEFAULT 'Neutral'")

        // Neutral: black, white, grey, beige, navy
        db.execSQL("UPDATE colors SET color_family = 'Neutral' WHERE name = 'Black'")
        db.execSQL("UPDATE colors SET color_family = 'Neutral' WHERE name = 'White'")
        db.execSQL("UPDATE colors SET color_family = 'Neutral' WHERE name = 'Grey'")
        db.execSQL("UPDATE colors SET color_family = 'Neutral' WHERE name = 'Beige'")
        db.execSQL("UPDATE colors SET color_family = 'Neutral' WHERE name = 'Navy'")

        // Earth: brown, tan, olive
        db.execSQL("UPDATE colors SET color_family = 'Earth' WHERE name = 'Brown'")
        db.execSQL("UPDATE colors SET color_family = 'Earth' WHERE name = 'Tan'")
        db.execSQL("UPDATE colors SET color_family = 'Earth' WHERE name = 'Olive'")

        // Cool: royal blue, sky blue, forest green, mint, purple
        db.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Royal Blue'")
        db.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Sky Blue'")
        db.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Forest Green'")
        db.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Mint'")
        db.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Purple'")

        // Warm: red, burgundy, pink, orange, yellow
        db.execSQL("UPDATE colors SET color_family = 'Warm' WHERE name = 'Red'")
        db.execSQL("UPDATE colors SET color_family = 'Warm' WHERE name = 'Burgundy'")
        db.execSQL("UPDATE colors SET color_family = 'Warm' WHERE name = 'Pink'")
        db.execSQL("UPDATE colors SET color_family = 'Warm' WHERE name = 'Orange'")
        db.execSQL("UPDATE colors SET color_family = 'Warm' WHERE name = 'Yellow'")

        // Bright: no seeded colors land here by default — future user-added colors
        // can be assigned Bright via the UI. The DEFAULT 'Neutral' covers any gap.
    }
}