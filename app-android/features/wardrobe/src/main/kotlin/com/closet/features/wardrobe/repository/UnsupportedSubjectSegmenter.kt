package com.closet.features.wardrobe.repository

import android.graphics.Bitmap

/**
 * No-op [SubjectSegmenter] for builds without Play Services.
 *
 * `play-services-mlkit-subject-segmentation` is a GMS dependency not included in
 * the FOSS build. This stub satisfies the Koin binding so the rest of the codebase
 * compiles unchanged. [removeBackground] throws [UnsupportedOperationException];
 * the calling ViewModel catches it and emits an error snackbar.
 */
class UnsupportedSubjectSegmenter : SubjectSegmenter {

    /** `false` in the FOSS flavor — hides the "Remove background" button entirely. */
    override val isSupported: Boolean = false

    override suspend fun isModelDownloaded(): Boolean = false

    override suspend fun ensureModelDownloaded() { /* no-op in FOSS builds */ }

    override suspend fun removeBackground(bitmap: Bitmap): Bitmap =
        throw UnsupportedOperationException("Background removal is not available in the FOSS build")
}
