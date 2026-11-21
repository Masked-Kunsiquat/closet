package com.closet.features.wardrobe.photo

import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade

/**
 * The platform half of the item photo pipeline: storage, background removal, color extraction
 * and captioning. [com.closet.features.wardrobe.ClothingFormViewModel] keeps the orchestration
 * (guards, state, the caption job); this interface only does the platform work.
 */
interface ItemPhotoProcessor {
    val isSegmentationSupported: Boolean
    val isCaptionSupported: Boolean
    /** Copies a picked image (platform URI string) into app storage; returns its relative path. */
    suspend fun importPhoto(source: String): String
    suspend fun deleteImage(relativePath: String)
    suspend fun ensureSegmentationModel()
    /** Background-removed copy saved as a new PNG; returns its relative path. Throws on failure. */
    suspend fun removeBackground(relativePath: String): String
    /**
     * The image's dominant colours, measured by the shared ColorExtractor and named by the nearest
     * of [shades]; alpha is the mask on a segmented image. Empty on any failure, so the add/edit
     * form can treat "no colours" and "could not measure" alike. Use [measureColors] to tell them apart.
     */
    suspend fun extractColors(relativePath: String, shades: List<ReferenceShade>): List<ExtractedColor>
    /**
     * Like [extractColors], but a failure is not hidden: throws when the image cannot be decoded
     * (missing file, undecodable) or the extraction fails. Returns a possibly empty list only when
     * the measurement genuinely ran. Cancellation propagates.
     */
    suspend fun measureColors(relativePath: String, shades: List<ReferenceShade>): List<ExtractedColor>
    /** Caption, or null if the image can't be decoded. Throws if captioning fails. */
    suspend fun describe(relativePath: String): String?
}
