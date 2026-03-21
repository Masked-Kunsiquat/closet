package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.closet.core.data.DatabaseSeeder

/**
 * Migration 1→2: Add layout columns to outfit_items and populate colors.
 * Columns are checked before adding to avoid errors if they already exist in some v1 versions.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "outfit_items", "pos_x")) {
            db.execSQL("ALTER TABLE outfit_items ADD COLUMN pos_x REAL")
        }
        if (!columnExists(db, "outfit_items", "pos_y")) {
            db.execSQL("ALTER TABLE outfit_items ADD COLUMN pos_y REAL")
        }
        if (!columnExists(db, "outfit_items", "scale")) {
            db.execSQL("ALTER TABLE outfit_items ADD COLUMN scale REAL")
        }
        if (!columnExists(db, "outfit_items", "z_index")) {
            db.execSQL("ALTER TABLE outfit_items ADD COLUMN z_index INTEGER")
        }

        DatabaseSeeder.seedColors(db)
    }
}
