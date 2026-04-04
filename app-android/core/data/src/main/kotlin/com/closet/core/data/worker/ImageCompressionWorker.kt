package com.closet.core.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.StorageRepository.Companion.IMAGES_DIR_NAME
import com.closet.core.data.repository.StorageRepository.Companion.JPEG_QUALITY
import com.closet.core.data.repository.StorageRepository.Companion.MAX_DIMENSION
import com.closet.core.data.worker.ImageCompressionWork.KEY_DONE
import com.closet.core.data.worker.ImageCompressionWork.KEY_FAILED
import com.closet.core.data.worker.ImageCompressionWork.KEY_SKIPPED
import com.closet.core.data.worker.ImageCompressionWork.KEY_TOTAL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Background worker that re-encodes oversized images already stored in `closet_images/`.
 *
 * An image is a candidate if its longest edge exceeds [MAX_DIMENSION] (1600 px) OR its file
 * size exceeds [MAX_FILE_BYTES] (1.5 MB). Images that already meet both thresholds are
 * skipped to avoid redundant work.
 *
 * For each candidate:
 * 1. Decode with power-of-two [BitmapFactory.Options.inSampleSize] (cheap, no-alloc first pass).
 * 2. Final [Bitmap.createScaledBitmap] if the long edge is still above [MAX_DIMENSION].
 * 3. Re-encode to a temp file, then atomically replace the original **only if the temp is smaller**.
 *    Same filename → no database update required.
 *
 * Format policy: extension-first — JPEG for `.jpg`, PNG for `.png`, WEBP_LOSSY/LOSSLESS for `.webp` (API 30+).
 *
 * Scheduled as a one-time job by [ImageCompressionScheduler.schedule] on every app start
 * (idle + battery-not-low, 30 s initial delay, KEEP policy). Can also be triggered immediately
 * via [ImageCompressionScheduler.runNow] from Settings.
 */
@HiltWorker
class ImageCompressionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val clothingDao: ClothingDao,
) : CoroutineWorker(context, params) {

    companion object {
        private const val MAX_FILE_BYTES = 1_500_000L // 1.5 MB
        private const val TAG = "ImageCompressionWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val imagesDir = File(applicationContext.filesDir, IMAGES_DIR_NAME)
        val imagesDirCanonical = imagesDir.canonicalPath + File.separator
        val paths = clothingDao.getAllImagePaths()
        val total = paths.size

        if (total == 0) {
            Timber.tag(TAG).d("Nothing to compress")
            return@withContext Result.success(
                workDataOf(KEY_DONE to 0, KEY_TOTAL to 0, KEY_SKIPPED to 0, KEY_FAILED to 0),
            )
        }

        var done = 0
        var skipped = 0
        var failed = 0

        for (relativePath in paths) {
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_SKIPPED to skipped, KEY_FAILED to failed))

            val file = File(imagesDir, relativePath)
            // Reject any path that escapes closet_images/ via absolute paths or ../ traversal.
            if (!file.canonicalPath.startsWith(imagesDirCanonical)) {
                Timber.tag(TAG).w("Path escapes images dir, skipping: %s", relativePath)
                skipped++
                continue
            }
            if (!file.exists()) {
                skipped++
                continue
            }

            try {
                if (!needsCompression(file)) {
                    skipped++
                    continue
                }
                compressInPlace(file)
                done++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to compress %s", relativePath)
                failed++
            }
        }

        Timber.tag(TAG).d("done=%d skipped=%d failed=%d total=%d", done, skipped, failed, total)
        Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_SKIPPED to skipped, KEY_FAILED to failed))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun needsCompression(file: File): Boolean {
        if (file.length() > MAX_FILE_BYTES) return true
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IOException("Failed to decode image bounds: ${file.absolutePath}")
        }
        return maxOf(opts.outWidth, opts.outHeight) > MAX_DIMENSION
    }

    private fun compressInPlace(file: File) {
        // First pass: bounds only.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)

        // Compute power-of-two inSampleSize.
        var sampleSize = 1
        while (longest / sampleSize > MAX_DIMENSION) sampleSize *= 2

        // Second pass: decode at computed sample size.
        val sampled = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IOException("Failed to decode image: ${file.absolutePath}")

        // Final scale if still above threshold.
        val finalBitmap = if (maxOf(sampled.width, sampled.height) > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(sampled.width, sampled.height)
            val scaled = Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * scale).toInt(),
                (sampled.height * scale).toInt(),
                /* filter= */ true,
            )
            sampled.recycle()
            scaled
        } else {
            sampled
        }

        val format = formatFor(file.extension.lowercase(), finalBitmap.hasAlpha())
            ?: throw IOException("Unsupported image extension: ${file.extension}")
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else JPEG_QUALITY
        val temp = File.createTempFile(file.nameWithoutExtension, ".tmp", file.parentFile)

        try {
            temp.outputStream().use { out ->
                if (!finalBitmap.compress(format, quality, out)) {
                    throw IOException("Bitmap.compress returned false for ${file.name}")
                }
            }
            // Only replace if the re-encoded file is actually smaller.
            if (temp.length() < file.length()) {
                if (!temp.renameTo(file)) {
                    throw IOException("Atomic rename failed for ${file.name}")
                }
            } else {
                temp.delete()
            }
        } finally {
            finalBitmap.recycle()
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Picks the best [Bitmap.CompressFormat] to re-encode a file, keeping the original extension
     * as the primary discriminator so the file's container format never changes.
     *
     * - `.jpg` / `.jpeg` → JPEG
     * - `.png`           → PNG (always, regardless of alpha — keeps lossless semantics)
     * - `.webp`          → WEBP_LOSSY (no alpha, API 30+) or WEBP_LOSSLESS (alpha, API 30+);
     *                       null on API < 30 (caller throws IOException)
     * - unknown          → null (caller throws IOException)
     */
    private fun formatFor(ext: String, hasAlpha: Boolean): Bitmap.CompressFormat? = when (ext) {
        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasAlpha) {
                @Suppress("NewApi")
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("NewApi")
                Bitmap.CompressFormat.WEBP_LOSSY
            }
        } else {
            null
        }
        else -> null
    }
}
