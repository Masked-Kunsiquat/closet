package com.closet.core.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

/**
 * Shared bitmap utilities used by ViewModels that decode images for on-device ML inference.
 */
object BitmapUtils {

    /**
     * Decodes [path] into a [Bitmap] at a sample size that keeps the longest side ≤ [maxDim].
     * Uses a two-pass decode (bounds-only then sampled) so the full-resolution image is never
     * allocated in memory. Both passes ask for sRGB, so wide-gamut (e.g. Display P3) photos are
     * converted on decode and colour measurement sees sRGB values.
     *
     * Returns `null` if the file cannot be decoded.
     */
    fun decodeSampledBitmap(path: String, maxDim: Int): Bitmap? {
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredColorSpace = srgb
        }
        BitmapFactory.decodeFile(path, opts)
        val longest = maxOf(opts.outWidth, opts.outHeight)
        var sampleSize = 1
        while (longest / sampleSize > maxDim) sampleSize *= 2
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredColorSpace = srgb
            },
        )
    }
}
