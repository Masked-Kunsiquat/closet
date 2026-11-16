package com.closet.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sums the files under [imagesDir]. */
class JvmImageStorageStats(private val imagesDir: File) : ImageStorageStats {
    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        imagesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
