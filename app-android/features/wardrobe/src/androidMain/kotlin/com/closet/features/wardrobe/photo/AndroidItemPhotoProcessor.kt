package com.closet.features.wardrobe.photo

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import co.touchlab.kermit.Logger
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.BitmapUtils
import com.closet.core.data.util.ColorMatcher
import com.closet.features.wardrobe.repository.SubjectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android [ItemPhotoProcessor]: app storage through [StorageRepository], background removal
 * through [SubjectSegmenter], Palette color extraction and captions from [CaptionEnrichmentProvider].
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

    // The Palette API filters out transparent pixels, so on a segmented PNG only subject
    // colours are sampled — not the background.
    override suspend fun extractColors(
        relativePath: String,
        available: List<ColorEntity>,
    ): List<ColorEntity> = withContext(Dispatchers.Default) {
        val file = storageRepository.getFile(relativePath)
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val extracted = listOfNotNull(
                    palette.dominantSwatch?.rgb,
                    palette.vibrantSwatch?.rgb,
                    palette.mutedSwatch?.rgb
                ).distinct()

                if (extracted.isNotEmpty()) {
                    val matched = extracted.map { ColorMatcher.findNearestColor(it, available) }.distinct()
                    if (matched.isNotEmpty()) {
                        log.d { "extractColors: ${file.name} → ${matched.joinToString { it.name }}" }
                    }
                    matched
                } else {
                    log.d { "extractColors: ${file.name} → no swatches extracted" }
                    emptyList()
                }
            } else {
                log.d { "extractColors: ${file.name} → bitmap decode returned null" }
                emptyList()
            }
        } catch (e: Exception) {
            log.d(e) { "color extraction failed" }
            emptyList()
        }
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
