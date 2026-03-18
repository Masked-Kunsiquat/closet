package com.closet.core.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing local image storage.
 * Follows the "Relative paths ONLY" rule from engineering conventions.
 */
@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imagesDir = File(context.filesDir, "closet_images").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Saves an image from a Uri to the app's internal storage.
     * @param uri The source Uri (from camera or picker).
     * @return The relative path (filename) of the saved image.
     */
    suspend fun saveImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val destFile = File(imagesDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream from Uri: $uri")

        fileName
    }

    /**
     * Deletes an image from storage.
     * @param relativePath The filename stored in the database.
     */
    suspend fun deleteImage(relativePath: String) = withContext(Dispatchers.IO) {
        val file = File(imagesDir, relativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Returns the absolute File object for a relative path.
     * Used by image loading libraries like Coil.
     */
    fun getFile(relativePath: String): File {
        return File(imagesDir, relativePath)
    }

    /**
     * Checks if a file exists for a given relative path.
     */
    fun exists(relativePath: String): Boolean {
        return File(imagesDir, relativePath).exists()
    }
}
