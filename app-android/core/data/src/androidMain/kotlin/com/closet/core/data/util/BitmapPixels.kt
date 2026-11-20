package com.closet.core.data.util

import android.graphics.Bitmap

/**
 * Row-major, non-premultiplied sRGB ARGB pixels (`width * height` long), the input
 * [com.closet.core.data.color.ColorExtractor] takes; alpha carries a segmentation mask.
 * A hardware bitmap (whose pixels can't be read) is copied to software memory first.
 */
fun Bitmap.toArgbPixels(): IntArray {
    val readable = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val pixels = IntArray(readable.width * readable.height)
    readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
    if (readable !== this) readable.recycle()
    return pixels
}
