package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 4→5: Add case-insensitive uniqueness to brands via a normalized_name column.
 *
 * Steps:
 *   1. Add normalized_name column (NOT NULL, backfilled from LOWER(name)).
 *   2. Create a unique index on normalized_name.
 *   3. Drop the old case-sensitive unique index on name.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add normalized_name column with a temporary default; backfill immediately.
        db.execSQL("ALTER TABLE brands ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE brands SET normalized_name = LOWER(name)")

        // 2. Create unique index on normalized_name.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_brands_normalized_name` ON `brands` (`normalized_name`)"
        )

        // 3. Drop the old case-sensitive unique index on name.
        db.execSQL("DROP INDEX IF EXISTS `index_brands_name`")
    }
}
