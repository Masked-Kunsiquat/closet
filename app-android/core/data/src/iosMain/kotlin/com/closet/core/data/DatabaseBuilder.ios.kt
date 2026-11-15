package com.closet.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private const val DATABASE_NAME = "closet.db"

/**
 * Returns a [ClothingDatabase] builder for `closet.db` in the app's documents directory, with
 * the shared [configureClosetDatabase] settings applied. Call `build()` on the result.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<ClothingDatabase> =
    Room.databaseBuilder<ClothingDatabase>(name = documentDirectory() + "/" + DATABASE_NAME)
        .configureClosetDatabase()

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(directory?.path) { "No documents directory" }
}
