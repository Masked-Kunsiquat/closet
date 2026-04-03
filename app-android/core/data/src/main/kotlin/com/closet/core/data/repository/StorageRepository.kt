package com.closet.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing local image storage.
 * Follows the "Relative paths ONLY" rule from engineering conventions.
 */
@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
    }

    private val imagesDir = File(context.filesDir, "closet_images").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Saves an image from a Uri to the app's internal storage, resizing and re-encoding to JPEG.
     *
     * Uses a two-pass decode: first reads image bounds without allocating a full bitmap, then
     * decodes at the largest power-of-two [inSampleSize] that keeps the longest edge ≤ [MAX_DIMENSION].
     * A final [Bitmap.createScaledBitmap] handles any remainder after power-of-two sampling.
     *
     * @param uri The source Uri (from camera or picker).
     * @return The relative path (filename) of the saved image.
     */
    suspend fun saveImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val destFile = File(imagesDir, fileName)

        // First pass: read bounds only (no pixel allocation).
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        } ?: throw IllegalStateException("Could not open input stream from Uri: $uri")

        // Compute largest power-of-two inSampleSize that keeps longest edge ≤ MAX_DIMENSION.
        val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (longest / sampleSize > MAX_DIMENSION) sampleSize *= 2

        // Second pass: decode at computed sample size.
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: throw IllegalStateException("Could not decode bitmap from Uri: $uri")

        // Final scale-down if the long edge is still > MAX_DIMENSION after power-of-two sampling.
        val finalBitmap = if (maxOf(sampled.width, sampled.height) > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(sampled.width, sampled.height)
            val scaled = Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * scale).toInt(),
                (sampled.height * scale).toInt(),
                /* filter= */ true
            )
            sampled.recycle()
            scaled
        } else {
            sampled
        }

        try {
            destFile.outputStream().use { out ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    destFile.delete()
                    throw IOException("Bitmap.compress returned false for $fileName")
                }
            }
        } finally {
            finalBitmap.recycle()
        }

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
    @Suppress("unused")
    fun exists(relativePath: String): Boolean {
        return File(imagesDir, relativePath).exists()
    }

    /**
     * Saves a [Bitmap] as a PNG to the app's internal images directory.
     *
     * The PNG encoder ignores the quality parameter, so lossless transparency is
     * preserved — required for segmented images with transparent backgrounds.
     *
     * @param bitmap The bitmap to save (should be ARGB_8888 for transparency support).
     * @param filename The target filename including extension (e.g. `"uuid.png"`).
     * @return The relative path (filename only, no leading slash) — same convention as [saveImage].
     */
    suspend fun saveBitmap(bitmap: Bitmap, filename: String): String = withContext(Dispatchers.IO) {
        val destFile = File(imagesDir, filename)
        destFile.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                destFile.delete()
                throw IOException("Bitmap.compress returned false for $filename")
            }
        }
        filename
    }

    /**
     * Loads a Bitmap from a Uri.
     */
    suspend fun getBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bitmap from Uri: $uri")
            null
        }
    }
}
