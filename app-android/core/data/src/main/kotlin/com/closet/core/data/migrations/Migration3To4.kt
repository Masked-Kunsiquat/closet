package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.closet.core.data.DatabaseSeeder

/**
 * Migration 3→4: Introduce the brands lookup table and migrate the free-text brand column
 * to a proper FK relationship.
 *
 * Steps:
 *   1. Create brands table with unique index on name.
 *   2. Backfill brands from distinct values in clothing_items.brand (user data first).
 *   3. Add brand_id FK column to clothing_items.
 *   4. Populate brand_id by matching the backfilled names.
 *   5. Seed common brands (INSERT OR IGNORE — backfilled rows are not overwritten).
 *
 * Note: The old `brand` TEXT column is kept in place. SQLite cannot drop columns before
 * API 35, and a table recreation is not worth the risk here. The column is no longer
 * written to by new code; reads go through the brand_id join.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create brands table
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `brands` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_brands_name` ON `brands` (`name`)"
        )

        // 2. Backfill brands from existing free-text data (trim to avoid inserting whitespace-only values)
        db.execSQL(
            "INSERT OR IGNORE INTO brands (name) " +
            "SELECT DISTINCT TRIM(brand) FROM clothing_items " +
            "WHERE TRIM(brand) IS NOT NULL AND TRIM(brand) != ''"
        )

        // 3. Add brand_id FK column (guard against schemas that already have the column)
        if (!columnExists(db, "clothing_items", "brand_id")) {
            db.execSQL("ALTER TABLE clothing_items ADD COLUMN brand_id INTEGER REFERENCES brands(id) ON DELETE SET NULL")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clothing_items_brand_id` ON `clothing_items` (`brand_id`)")

        // 4. Populate brand_id from backfilled rows (match on trimmed value)
        db.execSQL(
            "UPDATE clothing_items " +
            "SET brand_id = (SELECT id FROM brands WHERE brands.name = TRIM(clothing_items.brand)) " +
            "WHERE TRIM(brand) IS NOT NULL AND TRIM(brand) != ''"
        )

        // 5. Seed common starter brands (user backfill takes priority via INSERT OR IGNORE above)
        DatabaseSeeder.seedBrands(db)

        // 6. Ensure the one-OOTD-per-day partial index exists on upgraded databases.
        //    Fresh installs get this via the onCreate callback; migrations must add it explicitly.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1")
    }
}
