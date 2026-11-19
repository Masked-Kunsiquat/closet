package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema v1 -> v2 (colour v2, #81): measured item colours, basic colour terms, the wears-as-neutral
 * flag and reference shades.
 *
 * - `clothing_item_colors` gains `hex` and `share` (null on existing rows) and `source`, so every
 *   existing row becomes a `user` row: the user picked those colours.
 * - `colors` gains `basic_term` and `wears_as_neutral`, backfilled for the 18 v1 colours; their
 *   `hex` and `color_family` are unchanged.
 * - Charcoal, Cream, Denim and Teal are added, and `color_shades` is created and seeded (83 rows).
 *
 * Frozen literal SQL (see AGENTS.md): it must not read `DatabaseSeeder`. `MigrationTest` checks
 * the migrated colours and shades equal a fresh install's.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Room cannot represent the partial index; onOpen recreates it right after the migration.
        connection.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        // Measured colour rows. Existing rows were picked by the user.
        connection.execSQL("ALTER TABLE clothing_item_colors ADD COLUMN hex TEXT")
        connection.execSQL("ALTER TABLE clothing_item_colors ADD COLUMN share REAL")
        connection.execSQL("ALTER TABLE clothing_item_colors ADD COLUMN source TEXT NOT NULL DEFAULT 'user'")

        // Basic colour term and the neutral flag.
        connection.execSQL("ALTER TABLE colors ADD COLUMN basic_term TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE colors ADD COLUMN wears_as_neutral INTEGER NOT NULL DEFAULT 0")

        // Reference shades (SQL as Room generates it in schemas/.../2.json).
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `color_shades` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`color_id` INTEGER NOT NULL, `hex` TEXT NOT NULL, FOREIGN KEY(`color_id`) REFERENCES `colors`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_color_shades_color_id` ON `color_shades` (`color_id`)")

        // Backfill the 18 v1 colours.
        connection.execSQL("UPDATE colors SET basic_term = 'black', wears_as_neutral = 1 WHERE name = 'Black'")
        connection.execSQL("UPDATE colors SET basic_term = 'white', wears_as_neutral = 1 WHERE name = 'White'")
        connection.execSQL("UPDATE colors SET basic_term = 'gray', wears_as_neutral = 1 WHERE name = 'Grey'")
        connection.execSQL("UPDATE colors SET basic_term = 'brown', wears_as_neutral = 1 WHERE name = 'Beige'")
        connection.execSQL("UPDATE colors SET basic_term = 'blue', wears_as_neutral = 0 WHERE name = 'Navy'")
        connection.execSQL("UPDATE colors SET basic_term = 'red', wears_as_neutral = 0 WHERE name = 'Red'")
        connection.execSQL("UPDATE colors SET basic_term = 'blue', wears_as_neutral = 0 WHERE name = 'Royal Blue'")
        connection.execSQL("UPDATE colors SET basic_term = 'blue', wears_as_neutral = 0 WHERE name = 'Sky Blue'")
        connection.execSQL("UPDATE colors SET basic_term = 'green', wears_as_neutral = 0 WHERE name = 'Forest Green'")
        connection.execSQL("UPDATE colors SET basic_term = 'green', wears_as_neutral = 0 WHERE name = 'Olive'")
        connection.execSQL("UPDATE colors SET basic_term = 'green', wears_as_neutral = 0 WHERE name = 'Mint'")
        connection.execSQL("UPDATE colors SET basic_term = 'red', wears_as_neutral = 0 WHERE name = 'Burgundy'")
        connection.execSQL("UPDATE colors SET basic_term = 'pink', wears_as_neutral = 0 WHERE name = 'Pink'")
        connection.execSQL("UPDATE colors SET basic_term = 'orange', wears_as_neutral = 0 WHERE name = 'Orange'")
        connection.execSQL("UPDATE colors SET basic_term = 'yellow', wears_as_neutral = 0 WHERE name = 'Yellow'")
        connection.execSQL("UPDATE colors SET basic_term = 'purple', wears_as_neutral = 0 WHERE name = 'Purple'")
        connection.execSQL("UPDATE colors SET basic_term = 'brown', wears_as_neutral = 1 WHERE name = 'Brown'")
        connection.execSQL("UPDATE colors SET basic_term = 'brown', wears_as_neutral = 1 WHERE name = 'Tan'")

        // The 4 new colours (ids 19-22 on a v1 database seeded in order).
        connection.execSQL(
            "INSERT INTO colors (name, hex, color_family, basic_term, wears_as_neutral) " +
                "SELECT 'Charcoal', '#36454F', 'Neutral', 'gray', 1 WHERE NOT EXISTS (SELECT 1 FROM colors WHERE name = 'Charcoal')"
        )
        connection.execSQL(
            "INSERT INTO colors (name, hex, color_family, basic_term, wears_as_neutral) " +
                "SELECT 'Cream', '#F3EBD3', 'Neutral', 'white', 1 WHERE NOT EXISTS (SELECT 1 FROM colors WHERE name = 'Cream')"
        )
        connection.execSQL(
            "INSERT INTO colors (name, hex, color_family, basic_term, wears_as_neutral) " +
                "SELECT 'Denim', '#4F6D8F', 'Neutral', 'blue', 1 WHERE NOT EXISTS (SELECT 1 FROM colors WHERE name = 'Denim')"
        )
        connection.execSQL(
            "INSERT INTO colors (name, hex, color_family, basic_term, wears_as_neutral) " +
                "SELECT 'Teal', '#1F7A7A', 'Cool', 'blue', 0 WHERE NOT EXISTS (SELECT 1 FROM colors WHERE name = 'Teal')"
        )

        // Reference shades. A colour's legacy hex is not automatically one of its shades.
        // Black
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#000000' FROM colors WHERE name = 'Black'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#1A1A1A' FROM colors WHERE name = 'Black'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2B2B2B' FROM colors WHERE name = 'Black'")
        // White
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFFFFF' FROM colors WHERE name = 'White'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F4F4F2' FROM colors WHERE name = 'White'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#EDEDE8' FROM colors WHERE name = 'White'")
        // Grey
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#808080' FROM colors WHERE name = 'Grey'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#A0A0A0' FROM colors WHERE name = 'Grey'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#6E6E6E' FROM colors WHERE name = 'Grey'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#BEBEBE' FROM colors WHERE name = 'Grey'")
        // Beige
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F5F5DC' FROM colors WHERE name = 'Beige'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#E8DCC4' FROM colors WHERE name = 'Beige'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#D9C8A9' FROM colors WHERE name = 'Beige'")
        // Navy
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#000080' FROM colors WHERE name = 'Navy'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#1F2A44' FROM colors WHERE name = 'Navy'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#202A5A' FROM colors WHERE name = 'Navy'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2C3E63' FROM colors WHERE name = 'Navy'")
        // Red
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FF0000' FROM colors WHERE name = 'Red'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#B3202A' FROM colors WHERE name = 'Red'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#D62B2B' FROM colors WHERE name = 'Red'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#C41E3A' FROM colors WHERE name = 'Red'")
        // Royal Blue
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#4169E1' FROM colors WHERE name = 'Royal Blue'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2E50A8' FROM colors WHERE name = 'Royal Blue'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#3A5FCD' FROM colors WHERE name = 'Royal Blue'")
        // Sky Blue
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#87CEEB' FROM colors WHERE name = 'Sky Blue'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#8DB8DE' FROM colors WHERE name = 'Sky Blue'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#A7C7E7' FROM colors WHERE name = 'Sky Blue'")
        // Forest Green
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#228B22' FROM colors WHERE name = 'Forest Green'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2E5E3A' FROM colors WHERE name = 'Forest Green'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#355E3B' FROM colors WHERE name = 'Forest Green'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#4F7942' FROM colors WHERE name = 'Forest Green'")
        // Olive
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#808000' FROM colors WHERE name = 'Olive'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#6B6B3A' FROM colors WHERE name = 'Olive'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#708238' FROM colors WHERE name = 'Olive'")
        // Mint
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#98FF98' FROM colors WHERE name = 'Mint'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#A8DCC0' FROM colors WHERE name = 'Mint'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#B2E0C8' FROM colors WHERE name = 'Mint'")
        // Burgundy
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#800020' FROM colors WHERE name = 'Burgundy'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#6D1A2B' FROM colors WHERE name = 'Burgundy'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#7B2D3A' FROM colors WHERE name = 'Burgundy'")
        // Pink
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFC0CB' FROM colors WHERE name = 'Pink'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F2A7B8' FROM colors WHERE name = 'Pink'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#E75480' FROM colors WHERE name = 'Pink'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F4C2C2' FROM colors WHERE name = 'Pink'")
        // Orange
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFA500' FROM colors WHERE name = 'Orange'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#E8742A' FROM colors WHERE name = 'Orange'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F28C28' FROM colors WHERE name = 'Orange'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FF7F50' FROM colors WHERE name = 'Orange'")
        // Yellow
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFFF00' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F2CF3A' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFFACD' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFF59D' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#E1AD01' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFD300' FROM colors WHERE name = 'Yellow'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F6E7A5' FROM colors WHERE name = 'Yellow'")
        // Purple
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#800080' FROM colors WHERE name = 'Purple'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#6A3D8F' FROM colors WHERE name = 'Purple'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#9B59B6' FROM colors WHERE name = 'Purple'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#B57EDC' FROM colors WHERE name = 'Purple'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#CDB4E0' FROM colors WHERE name = 'Purple'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#E6E6FA' FROM colors WHERE name = 'Purple'")
        // Brown
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#6B4423' FROM colors WHERE name = 'Brown'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#5C4033' FROM colors WHERE name = 'Brown'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#7B5B3A' FROM colors WHERE name = 'Brown'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#4E342E' FROM colors WHERE name = 'Brown'")
        // Tan
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#D2B48C' FROM colors WHERE name = 'Tan'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#C8A47E' FROM colors WHERE name = 'Tan'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#C3B091' FROM colors WHERE name = 'Tan'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#C19A6B' FROM colors WHERE name = 'Tan'")
        // Charcoal
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#36454F' FROM colors WHERE name = 'Charcoal'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#3C3C3C' FROM colors WHERE name = 'Charcoal'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#4A4A4A' FROM colors WHERE name = 'Charcoal'")
        // Cream
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFFDD0' FROM colors WHERE name = 'Cream'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#F3EBD3' FROM colors WHERE name = 'Cream'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FAF3E0' FROM colors WHERE name = 'Cream'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#FFF5DC' FROM colors WHERE name = 'Cream'")
        // Denim
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#3B5A80' FROM colors WHERE name = 'Denim'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#4F6D8F' FROM colors WHERE name = 'Denim'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#5B7BA6' FROM colors WHERE name = 'Denim'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2F4A6B' FROM colors WHERE name = 'Denim'")
        // Teal
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#008080' FROM colors WHERE name = 'Teal'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#1F7A7A' FROM colors WHERE name = 'Teal'")
        connection.execSQL("INSERT INTO color_shades (color_id, hex) SELECT id, '#2A9D8F' FROM colors WHERE name = 'Teal'")
    }
}
