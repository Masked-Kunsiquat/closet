package com.closet.features.wardrobe.repository

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor stub for [ImageCaptionRepository].
 *
 * `com.google.mlkit:genai-image-description` is an AICore-backed GMS dependency not
 * included in the FOSS build. This stub satisfies the Hilt binding so the rest of the
 * codebase compiles unchanged. [describe] throws [UnsupportedOperationException];
 * callers must check [isSupported] before invoking it.
 */
@Singleton
class ImageCaptionRepository @Inject constructor() {

    /** `false` in the FOSS flavor — callers should skip the caption path entirely. */
    val isSupported: Boolean = false

    suspend fun describe(bitmap: Bitmap): String =
        throw UnsupportedOperationException("Image captioning is not available in the FOSS build")

    suspend fun isModelDownloaded(): Boolean = false

    suspend fun ensureModelDownloaded() { /* no-op in FOSS builds */ }
}
