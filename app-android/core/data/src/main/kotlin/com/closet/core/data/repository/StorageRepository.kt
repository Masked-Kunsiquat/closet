package com.closet.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
        const val IMAGES_DIR_NAME = "closet_images"
    }

    private val imagesDir = File(context.filesDir, IMAGES_DIR_NAME).apply {
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
     * Saves a segmented [Bitmap] (with alpha) to the app's internal images directory.
     *
     * On API 30+: encodes as WEBP_LOSSY at [JPEG_QUALITY] — preserves alpha, ~50–70 % smaller than PNG.
     * On API 26–29: falls back to lossless PNG, but caps at [MAX_DIMENSION] px on the longest edge
     * to limit file size (alpha channel means we can't use lossy compression).
     *
     * @param bitmap The segmented bitmap (ARGB_8888 with transparent background).
     * @param baseName UUID-only base name without extension (e.g. `"550e8400-e29b-41d4-a716"`).
     *                 The appropriate extension is appended automatically based on API level.
     * @return The relative path (filename with extension) — same convention as [saveImage].
     */
    suspend fun saveBitmap(bitmap: Bitmap, baseName: String): String = withContext(Dispatchers.IO) {
        val (format, ext) = segmentedFormat()
        val filename = "$baseName.$ext"
        val destFile = File(imagesDir, filename)

        // For the PNG fallback (API < 30) apply the same longest-edge cap as saveImage.
        val toSave = if (format == Bitmap.CompressFormat.PNG &&
            maxOf(bitmap.width, bitmap.height) > MAX_DIMENSION
        ) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                /* filter= */ true
            )
            scaled
        } else {
            bitmap
        }

        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else JPEG_QUALITY
        try {
            destFile.outputStream().use { out ->
                if (!toSave.compress(format, quality, out)) {
                    destFile.delete()
                    throw IOException("Bitmap.compress returned false for $filename")
                }
            }
        } finally {
            if (toSave !== bitmap) toSave.recycle()
        }

        filename
    }

    /**
     * Returns the [Bitmap.CompressFormat] and file extension to use for segmented images.
     * WEBP_LOSSY (API 30+) supports alpha and is ~50–70 % smaller than an equivalent PNG.
     * PNG is the lossless fallback for older devices.
     */
    private fun segmentedFormat(): Pair<Bitmap.CompressFormat, String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("NewApi") // R == API 30, guarded by the version check above
            Bitmap.CompressFormat.WEBP_LOSSY to "webp"
        } else {
            Bitmap.CompressFormat.PNG to "png"
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
