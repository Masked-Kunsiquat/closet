package com.closet.core.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Shared bitmap utilities used by ViewModels that decode images for on-device ML inference.
 */
object BitmapUtils {

    /**
     * Decodes [path] into a [Bitmap] at a sample size that keeps the longest side ≤ [maxDim].
     * Uses a two-pass decode (bounds-only then sampled) so the full-resolution image is never
     * allocated in memory.
     *
     * Returns `null` if the file cannot be decoded.
     */
    fun decodeSampledBitmap(path: String, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val longest = maxOf(opts.outWidth, opts.outHeight)
        var sampleSize = 1
        while (longest / sampleSize > maxDim) sampleSize *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }
}
