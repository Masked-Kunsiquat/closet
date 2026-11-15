package com.closet.features.wardrobe.repository

import android.graphics.Bitmap

/** Removes the background from a clothing photo. Implementations are bound per build variant. */
interface SubjectSegmenter {

    /** `false` when background removal is unavailable; hides the "Remove background" button. */
    val isSupported: Boolean

    suspend fun isModelDownloaded(): Boolean

    suspend fun ensureModelDownloaded()

    suspend fun removeBackground(bitmap: Bitmap): Bitmap
}
