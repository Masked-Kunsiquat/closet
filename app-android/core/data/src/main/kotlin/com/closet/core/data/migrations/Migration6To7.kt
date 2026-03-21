package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 6→7: Add outfit_log_items snapshot table.
 *
 * Problem: getLogsForItem previously joined outfit_logs → outfit_items (live), so editing
 * an outfit retroactively changed which historical logs counted as wearing a given item.
 *
 * Fix: outfit_log_items records the exact set of clothing items present in an outfit at the
 * moment the log is written. getLogsForItem now joins against this snapshot, not the live table.
 *
 * Steps:
 *   1. Drop the one_ootd_per_day partial index (Room cannot represent it in annotations;
 *      it is recreated by ClothingDatabase.onOpen() on every open).
 *   2. Create outfit_log_items with a CASCADE FK to outfit_logs.
 *   3. Backfill existing logs: for each log whose outfit still exists, snapshot the current
 *      outfit_items rows. This is best-effort — logs for deleted outfits have no items to
 *      backfill and will simply not appear in getLogsForItem (same behaviour as before).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS outfit_log_items (
                outfit_log_id    INTEGER NOT NULL,
                clothing_item_id INTEGER NOT NULL,
                outfit_name      TEXT,
                PRIMARY KEY (outfit_log_id, clothing_item_id),
                FOREIGN KEY (outfit_log_id) REFERENCES outfit_logs(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_outfit_log_items_clothing_item_id " +
            "ON outfit_log_items(clothing_item_id)"
        )

        // Backfill: snapshot current outfit_items for every existing log where the outfit
        // still exists. INSERT OR IGNORE is safe to re-run if migration somehow runs twice.
        db.execSQL(
            """
            INSERT OR IGNORE INTO outfit_log_items (outfit_log_id, clothing_item_id, outfit_name)
            SELECT ol.id, oi.clothing_item_id, o.name
            FROM outfit_logs ol
            JOIN outfit_items oi ON oi.outfit_id = ol.outfit_id
            LEFT JOIN outfits  o  ON  o.id        = ol.outfit_id
            WHERE ol.outfit_id IS NOT NULL
            """.trimIndent()
        )
    }
}
