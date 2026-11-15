package com.closet.core.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * Returns a [ClothingDatabase] builder for [file] on the JVM, with the shared
 * [configureClosetDatabase] settings applied. Call `build()` on the result.
 */
fun getDatabaseBuilder(file: File): RoomDatabase.Builder<ClothingDatabase> =
    Room.databaseBuilder<ClothingDatabase>(name = file.absolutePath)
        .configureClosetDatabase()
