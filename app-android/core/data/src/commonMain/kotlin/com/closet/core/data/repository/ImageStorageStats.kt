package com.closet.core.data.repository

/** Reports how much disk the stored images use. */
fun interface ImageStorageStats {
    /** Total size in bytes of the files under the images directory, recursively; `0` if it doesn't exist. */
    suspend fun usedBytes(): Long
}
