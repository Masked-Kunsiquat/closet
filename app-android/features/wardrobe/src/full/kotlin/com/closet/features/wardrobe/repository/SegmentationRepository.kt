package com.closet.features.wardrobe.repository

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes the background from clothing item photos using ML Kit Subject Segmentation.
 *
 * Uses the traditional Vision ML API (`play-services-mlkit-subject-segmentation`) which
 * works on all devices via Google Play Services — no AICore or device-capability check
 * required. The ~200 KB model is delivered automatically via Play Services on first use.
 *
 * Input bitmaps are downsampled to a maximum of 1024 px on the longest side before
 * processing. The stored image will be the downsampled + masked PNG; original dimensions
 * are not restored. (Minimum 512×512 is required for accuracy per ML Kit docs.)
 */
@Singleton
class SegmentationRepository @Inject constructor() {

    /** `true` in the full flavor; used by the ViewModel to hide the button in FOSS builds. */
    val isSupported: Boolean = true

    /**
     * Removes the background from [bitmap] and returns a new [Bitmap] with a transparent
     * background (`ARGB_8888`).
     *
     * @throws IllegalStateException if the segmenter returns a null foreground bitmap.
     */
    suspend fun removeBackground(bitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val input = downsample(bitmap)
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val client = SubjectSegmentation.getClient(options)
        try {
            val image = InputImage.fromBitmap(input, 0)
            val result = client.process(image).await()
            result.foregroundBitmap
                ?: throw IllegalStateException("SubjectSegmenter returned null foregroundBitmap")
        } finally {
            client.close()
        }
    }

    /** Scales [bitmap] down so its longest side is at most 1024 px. Returns original if already small enough. */
    private fun downsample(bitmap: Bitmap): Bitmap {
        val maxDim = 1024
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
