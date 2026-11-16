package com.closet.core.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber

/** Sums the file sizes under the images directory that [IosImagePathResolver] resolves against. */
class IosImageStorageStats : ImageStorageStats {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun usedBytes(): Long = withContext(Dispatchers.Default) {
        val root = documentDirectory() + "/$IMAGES_DIR_NAME"
        val manager = NSFileManager.defaultManager
        val enumerator = manager.enumeratorAtPath(root) ?: return@withContext 0L
        var total = 0L
        while (true) {
            val relative = enumerator.nextObject() as? String ?: break
            val attributes = manager.attributesOfItemAtPath("$root/$relative", error = null) ?: continue
            if (attributes[NSFileType] != NSFileTypeRegular) continue
            total += (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
        }
        total
    }
}
