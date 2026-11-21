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

    /** The outcome of [propose]: colours found, nothing detected, or a failure to measure. */
    sealed interface Proposal {
        /** The measured colours; never empty. */
        data class Found(val colors: List<ExtractedColor>) : Proposal
        /** Nothing to propose: no image, or the photo was measured and no colour was detected. */
        data object Empty : Proposal
        /** The measurement could not run: the reference shades failed to load, or the photo could not be measured. */
        data class Failed(val cause: Throwable?) : Proposal
    }

    /**
     * Measures the image at [imagePath] (relative) against the reference shades.
     * [Proposal.Empty] when there is no image or nothing is detected; [Proposal.Failed] when the
     * shades fail to load or the measurement throws, including a photo that cannot be decoded ([ItemPhotoProcessor.measureColors] throws for it).
     * Cancellation propagates.
     */
    suspend fun propose(imagePath: String?): Proposal {
        val path = imagePath ?: return Proposal.Empty
        val shades = when (val result = referenceShades()) {
            is ShadesResult.Loaded -> result.shades
            is ShadesResult.Unavailable -> return Proposal.Failed(result.cause)
        }
        val colors = try {
            photoProcessor.measureColors(path, shades)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "colour extraction failed" }
            return Proposal.Failed(e)
        }
        return if (colors.isEmpty()) Proposal.Empty else Proposal.Found(colors)
    }

    /**
     * Replaces the item's colours with [proposed] as auto rows and clears its curated flag.
     * [measuredImagePath] is the photo [proposed] was measured from: nothing is written when the
     * item's photo is no longer that one at write time (background removal replaced it).
     * With [onlyIfUncurated], nothing is written when the item is curated at write time (the bulk
     * job's only-unedited mode, so an edit made mid-run is never overwritten).
     * @return true when written; false when [proposed] is empty, the photo changed, or the guard
     *   skipped the item.
     */
    suspend fun apply(
        itemId: Long,
        proposed: List<ExtractedColor>,
        measuredImagePath: String?,
        onlyIfUncurated: Boolean = false,
    ): DataResult<Boolean> =
        clothingRepository.applyRedetectedColors(itemId, proposed, measuredImagePath, onlyIfUncurated)

    private sealed interface ShadesResult {
        data class Loaded(val shades: List<ReferenceShade>) : ShadesResult
        data class Unavailable(val cause: Throwable?) : ShadesResult
    }

    /** The reference shades, loaded once per instance; [ShadesResult.Unavailable] when the load fails (retried next call). */
    private suspend fun referenceShades(): ShadesResult = shadesLock.withLock {
        cachedShades?.let { return@withLock ShadesResult.Loaded(it) }
        when (val result = lookupRepository.getReferenceShades()) {
            is DataResult.Success -> ShadesResult.Loaded(result.data.also { cachedShades = it })
            is DataResult.Error -> {
                log.w(result.throwable) { "reference shades failed to load; colour re-detection skipped" }
                ShadesResult.Unavailable(result.throwable)
            }
            DataResult.Loading -> {
                log.w { "reference shades not loaded; colour re-detection skipped" }
                ShadesResult.Unavailable(null)
            }
        }
    }
}
