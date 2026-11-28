package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema v4 -> v5 (two-stage engine, item warmth, #147): the user's overrides of an item's derived
 * warmth level and rain-ready flag.
 *
 * - `clothing_items.warmth_override` — warmth level 0..4, or NULL to use the derived level.
 * - `clothing_items.rain_ready_override` — 0/1, or NULL to use the derived flag.
 *
 * Both columns are nullable with no default, so every existing item starts with no override.
 *
 * Frozen literal SQL (see AGENTS.md).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        // Room cannot represent the partial index; onOpen recreates it right after the migration.
        connection.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        connection.execSQL("ALTER TABLE clothing_items ADD COLUMN warmth_override INTEGER")
        connection.execSQL("ALTER TABLE clothing_items ADD COLUMN rain_ready_override INTEGER")
    }
}
