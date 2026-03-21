package com.closet.core.data.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Returns true if [column] exists in [table], false otherwise.
 * Uses PRAGMA table_info — safe for use inside migrations.
 */
fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    val cursor = db.query("PRAGMA table_info($table)")
    cursor.use {
        val nameIndex = it.getColumnIndex("name")
        if (nameIndex == -1) return false
        while (it.moveToNext()) {
            if (it.getString(nameIndex) == column) return true
        }
    }
    return false
}
