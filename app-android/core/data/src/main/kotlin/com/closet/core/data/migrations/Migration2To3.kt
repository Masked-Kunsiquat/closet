package com.closet.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 2→3: Enforce one wear-log per outfit per day.
 * The unique index mirrors the OutfitLogEntity annotation added in the same change;
 * the app-level check in LogRepository.wearOutfitToday is the primary guard,
 * but the index acts as a hard safety net at the schema level.
 * Note: SQLite treats NULL as distinct, so rows with outfit_id = NULL are unaffected.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_outfit_logs_outfit_id_date` " +
            "ON `outfit_logs` (`outfit_id`, `date`)"
        )
    }
}
