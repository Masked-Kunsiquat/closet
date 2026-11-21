package com.closet.features.wardrobe.photo

import android.net.Uri
import co.touchlab.kermit.Logger
import com.closet.core.data.color.ColorExtractor
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.BitmapUtils
import com.closet.core.data.util.toArgbPixels
import com.closet.features.wardrobe.repository.SubjectSegmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android [ItemPhotoProcessor]: app storage through [StorageRepository], background removal
 * through [SubjectSegmenter], color extraction through the shared [ColorExtractor] and captions from
 * [CaptionEnrichmentProvider].
 */
class AndroidItemPhotoProcessor(
    private val storageRepository: StorageRepository,
    private val segmentationRepository: SubjectSegmenter,
    private val imageCaptionRepository: CaptionEnrichmentProvider,
) : ItemPhotoProcessor {

    private val log = Logger.withTag("AndroidItemPhotoProcessor")

    override val isSegmentationSupported: Boolean get() = segmentationRepository.isSupported

    override val isCaptionSupported: Boolean get() = imageCaptionRepository.isSupported

    override suspend fun importPhoto(source: String): String =
        storageRepository.saveImage(Uri.parse(source))

    override suspend fun deleteImage(relativePath: String) {
        storageRepository.deleteImage(relativePath)
    }

    // Always call ensureModelDownloaded() — isModelDownloaded() returns true
    // optimistically (no public Play Services status API), so using it as a gate
    // would permanently skip this step and hide first-run delivery failures.
    override suspend fun ensureSegmentationModel() = withContext(Dispatchers.IO) {
        segmentationRepository.ensureModelDownloaded()
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun removeBackground(relativePath: String): String = withContext(Dispatchers.IO) {
        val file = storageRepository.getFile(relativePath)
        // Decode at reduced resolution so the full-res bitmap never hits memory.
        // The repo's own downsample() becomes a no-op when the bitmap is already ≤1024px.
        val bitmap = BitmapUtils.decodeSampledBitmap(file.absolutePath, maxDim = 1024)
            ?: throw IllegalStateException("Could not decode image file: $relativePath")
        val masked = segmentationRepository.removeBackground(bitmap)
        storageRepository.saveBitmap(masked, Uuid.random().toString())
    }

    // Alpha is the mask: on a segmented PNG only subject pixels are measured, not the
    // removed background. Decoded at ≤512 px in sRGB; the extractor samples a fixed stride anyway.
    override suspend fun measureColors(
        relativePath: String,
        shades: List<ReferenceShade>,
    ): List<ExtractedColor> = withContext(Dispatchers.Default) {
        val file = storageRepository.getFile(relativePath)
        val bitmap = withContext(Dispatchers.IO) {
            BitmapUtils.decodeSampledBitmap(file.absolutePath, maxDim = 512)
        } ?: throw IllegalStateException("image could not be decoded: $relativePath")
        try {
            val extracted = ColorExtractor(shades).extract(bitmap.toArgbPixels(), bitmap.width, bitmap.height)
            log.d { "measureColors: ${file.name} → ${extracted.joinToString { "${it.colorId}:${it.hex}@${it.share}" }}" }
            extracted
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override suspend fun extractColors(
        relativePath: String,
        shades: List<ReferenceShade>,
    ): List<ExtractedColor> = try {
        measureColors(relativePath, shades)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.d(e) { "color extraction failed" }
        emptyList()
    }

    override suspend fun describe(relativePath: String): String? {
        val file = storageRepository.getFile(relativePath)
        // Decode on IO — file read + BitmapFactory is synchronous CPU/IO work.
        val bitmap = withContext(Dispatchers.IO) {
            BitmapUtils.decodeSampledBitmap(file.absolutePath, maxDim = 1024)
        } ?: return null
        try {
            // describe() must stay on Main — Image Description API requirement.
            return withContext(Dispatchers.Main) { imageCaptionRepository.describe(bitmap) }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
