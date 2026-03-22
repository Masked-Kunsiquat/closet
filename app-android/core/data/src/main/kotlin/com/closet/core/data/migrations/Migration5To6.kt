package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5→6: Add icon column to patterns table and backfill icon names.
 *
 * Steps:
 *   1. Drop the one_ootd_per_day partial index so Room's post-migration schema
 *      validation passes (Room cannot represent partial indices in entity annotations).
 *      The index is recreated in ClothingDatabase.onOpen() on every open.
 *   2. Add nullable icon column to patterns.
 *   3. Rename 'Ombre' → 'Ombré' for correct display.
 *   4. Backfill icon names for all 17 seeded patterns.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS one_ootd_per_day")

        db.execSQL("ALTER TABLE patterns ADD COLUMN icon TEXT")

        // Fix display name for Ombré before the icon backfill loop runs.
        db.execSQL("UPDATE patterns SET name = 'Ombré' WHERE name = 'Ombre'")

        val updates = listOf(
            "Solid" to "solid",
            "Striped" to "striped",
            "Plaid/Tartan" to "plaid_tartan",
            "Checkered" to "checkered",
            "Floral" to "floral",
            "Geometric" to "geometric",
            "Animal Print" to "animal_print",
            "Abstract" to "abstract",
            "Tie-Dye" to "tie_dye",
            "Camouflage" to "camouflage",
            "Paisley" to "paisley",
            "Polka Dot" to "polka_dot",
            "Houndstooth" to "houndstooth",
            "Graphic" to "graphic",
            "Color Block" to "color_block",
            "Ombré" to "ombre",
            "Other" to "other"
        )
        updates.forEach { (name, icon) ->
            db.execSQL("UPDATE patterns SET icon = ? WHERE name = ?", arrayOf(icon, name))
        }
    }
}
