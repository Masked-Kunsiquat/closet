package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema v3 -> v4 (colour v2 PR 2, decision D1): re-picks ten base hexes so the recommendation
 * engine's hue terms read clothing-realistic colours (a "Navy" garment is not `#000080`), and
 * makes Navy a Cool colour. `color_shades` and item colour rows are unchanged.
 *
 * Frozen literal SQL (see AGENTS.md); the values mirror `DatabaseSeeder.SEEDED_COLORS`, which
 * `MigrationTest` checks.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        // Room cannot represent the partial index; onOpen recreates it right after the migration.
        connection.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        connection.execSQL("UPDATE colors SET hex = '#6B4423' WHERE name = 'Brown'")
        connection.execSQL("UPDATE colors SET hex = '#1F2A44' WHERE name = 'Navy'")
        connection.execSQL("UPDATE colors SET hex = '#C41E3A' WHERE name = 'Red'")
        connection.execSQL("UPDATE colors SET hex = '#F28C28' WHERE name = 'Orange'")
        connection.execSQL("UPDATE colors SET hex = '#F2CF3A' WHERE name = 'Yellow'")
        connection.execSQL("UPDATE colors SET hex = '#6A3D8F' WHERE name = 'Purple'")
        connection.execSQL("UPDATE colors SET hex = '#708238' WHERE name = 'Olive'")
        connection.execSQL("UPDATE colors SET hex = '#A8DCC0' WHERE name = 'Mint'")
        connection.execSQL("UPDATE colors SET hex = '#355E3B' WHERE name = 'Forest Green'")
        connection.execSQL("UPDATE colors SET hex = '#E8DCC4' WHERE name = 'Beige'")
        connection.execSQL("UPDATE colors SET color_family = 'Cool' WHERE name = 'Navy'")
    }
}
