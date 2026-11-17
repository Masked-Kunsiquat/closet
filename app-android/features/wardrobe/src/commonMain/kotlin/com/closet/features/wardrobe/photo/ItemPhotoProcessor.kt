package com.closet.features.wardrobe.photo

import com.closet.core.data.model.ColorEntity

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
    /** Nearest seeded colors for the image's dominant/vibrant/muted swatches; empty on any failure. */
    suspend fun extractColors(relativePath: String, available: List<ColorEntity>): List<ColorEntity>
    /** Caption, or null if the image can't be decoded. Throws if captioning fails. */
    suspend fun describe(relativePath: String): String?
}
