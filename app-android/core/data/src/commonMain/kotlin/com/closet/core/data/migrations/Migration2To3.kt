package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema v2 -> v3 (re-detect colours, #128): `clothing_items.colors_curated`, set when the user
 * chose an item's colours, so automatic re-tagging never overrides them.
 *
 * Existing items are backfilled: an item with any `user` colour row is curated (every v1 colour
 * row became `user` in [MIGRATION_1_2]); items with only `auto` rows or none are not.
 *
 * Frozen literal SQL (see AGENTS.md).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        // Room cannot represent the partial index; onOpen recreates it right after the migration.
        connection.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        connection.execSQL("ALTER TABLE clothing_items ADD COLUMN colors_curated INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "UPDATE clothing_items SET colors_curated = 1 WHERE id IN " +
                "(SELECT DISTINCT clothing_item_id FROM clothing_item_colors WHERE source = 'user')"
        )
    }
}
