package com.closet.features.wardrobe.repository

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor stub for [SegmentationRepository].
 *
 * `play-services-mlkit-subject-segmentation` is a GMS dependency not included in
 * the FOSS build. This stub satisfies the Hilt binding so the rest of the codebase
 * compiles unchanged. [removeBackground] throws [UnsupportedOperationException];
 * the calling ViewModel catches it and emits an error snackbar.
 */
@Singleton
class SegmentationRepository @Inject constructor() {

    /** `false` in the FOSS flavor — hides the "Remove background" button entirely. */
    val isSupported: Boolean = false

    suspend fun removeBackground(bitmap: Bitmap): Bitmap =
        throw UnsupportedOperationException("Background removal is not available in the FOSS build")
}
