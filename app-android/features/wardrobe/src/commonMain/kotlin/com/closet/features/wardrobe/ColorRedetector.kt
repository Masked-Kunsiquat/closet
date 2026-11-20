package com.closet.features.wardrobe

import co.touchlab.kermit.Logger
import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.util.DataResult
import com.closet.features.wardrobe.photo.ItemPhotoProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Re-detects an item's colours from its photo: [propose] measures, [apply] writes. Shared by the
 * detail screen's per-item re-detect and the bulk re-detect job, so the decision logic is tested
 * once on the JVM.
 */
class ColorRedetector(
    private val photoProcessor: ItemPhotoProcessor,
    private val lookupRepository: LookupRepository,
    private val clothingRepository: ClothingRepository,
) {
    private val log = Logger.withTag("ColorRedetector")
    private val shadesLock = Mutex()
    private var cachedShades: List<ReferenceShade>? = null

    /**
     * The colours measured from the image at [imagePath] (relative), named by the reference shades.
     * Empty when there is no image, the shades fail to load, or the extraction finds nothing or fails.
     */
    suspend fun propose(imagePath: String?): List<ExtractedColor> {
        val path = imagePath ?: return emptyList()
        val shades = referenceShades() ?: return emptyList()
        return try {
            photoProcessor.extractColors(path, shades)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "colour extraction failed" }
            emptyList()
        }
    }

    /**
     * Replaces the item's colours with [proposed] as auto rows and clears its curated flag.
     * @return true when written, false when [proposed] is empty (nothing written).
     */
    suspend fun apply(itemId: Long, proposed: List<ExtractedColor>): DataResult<Boolean> =
        clothingRepository.applyRedetectedColors(itemId, proposed)

    /** The reference shades, loaded once per instance; null when the load fails (retried next call). */
    private suspend fun referenceShades(): List<ReferenceShade>? = shadesLock.withLock {
        cachedShades?.let { return@withLock it }
        when (val result = lookupRepository.getReferenceShades()) {
            is DataResult.Success -> result.data.also { cachedShades = it }
            is DataResult.Error -> {
                log.w(result.throwable) { "reference shades failed to load; colour re-detection skipped" }
                null
            }
            DataResult.Loading -> null
        }
    }
}
