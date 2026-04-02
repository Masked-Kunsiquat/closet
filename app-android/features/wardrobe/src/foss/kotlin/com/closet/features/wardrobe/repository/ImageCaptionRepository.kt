package com.closet.features.wardrobe.repository

import android.graphics.Bitmap
import com.closet.core.data.repository.CaptionEnrichmentProvider
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
class ImageCaptionRepository @Inject constructor() : CaptionEnrichmentProvider {

    /** `false` in the FOSS flavor — callers should skip the caption path entirely. */
    override val isSupported: Boolean = false

    override suspend fun describe(bitmap: Bitmap): String =
        throw UnsupportedOperationException("Image captioning is not available in the FOSS build")

    override suspend fun isModelDownloaded(): Boolean = false

    override suspend fun ensureModelDownloaded() { /* no-op in FOSS builds */ }

    override fun consumeResult() { /* no-op in FOSS builds */ }
}
