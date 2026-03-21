package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5→6: Add icon column to patterns table and backfill icon names.
 *
 * Steps:
 *   1. Add nullable icon column to patterns.
 *   2. Backfill icon names for all 17 seeded patterns.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
