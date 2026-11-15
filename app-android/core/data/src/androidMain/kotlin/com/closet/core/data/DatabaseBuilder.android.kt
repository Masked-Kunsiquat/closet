package com.closet.core.data

import android.content.Context
import androidx.room.Room

private const val DATABASE_NAME = "closet.db"

/** Android singleton state behind [getDatabase] and [closeAndReset]. */
private object ClothingDatabaseHolder {
    @Volatile
    var instance: ClothingDatabase? = null
}

/**
 * Closes the current database connection and clears the singleton so the next call to
 * [getDatabase] builds a fresh instance. Called by `RestoreRepository` before overwriting
 * the database file; the process is restarted afterward so stale DAO references are never
 * used in production code.
 */
fun ClothingDatabase.Companion.closeAndReset() {
    synchronized(ClothingDatabaseHolder) {
        ClothingDatabaseHolder.instance?.close()
        ClothingDatabaseHolder.instance = null
    }
}

/**
 * Returns the singleton [ClothingDatabase] instance, creating it if needed. The file is
 * `closet.db` under [Context.getDatabasePath], the same file backup and restore copy.
 * [configureClosetDatabase] registers seeding, the unique partial index and the
 * category-consistency triggers.
 */
fun ClothingDatabase.Companion.getDatabase(context: Context): ClothingDatabase {
    return ClothingDatabaseHolder.instance ?: synchronized(ClothingDatabaseHolder) {
        ClothingDatabaseHolder.instance ?: run {
            val appContext = context.applicationContext
            val instance = Room.databaseBuilder<ClothingDatabase>(
                appContext,
                appContext.getDatabasePath(DATABASE_NAME).absolutePath,
            )
                .configureClosetDatabase()
                .build()
            ClothingDatabaseHolder.instance = instance
            instance
        }
    }
}
