package com.closet.core.data.repository

import android.graphics.Bitmap
import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the ML Kit GenAI Image Description API exposed to modules that
 * cannot directly depend on `features/wardrobe` (e.g. `features/settings`).
 *
 * The full-flavor implementation delegates to [com.closet.features.wardrobe.repository.ImageCaptionRepository].
 * The FOSS-flavor stub sets [isSupported] = `false` and throws on [describe].
 *
 * Provided by [com.closet.features.wardrobe.di.WardrobeModule], following the same
 * pattern as [BatchSegmentationScheduler].
 */
interface CaptionEnrichmentProvider {

    /** `true` in full builds; `false` in FOSS builds. Gate all caption paths on this. */
    val isSupported: Boolean

    /**
     * Generates a one-sentence photo caption for [bitmap].
     * Must be called from the main thread.
     *
     * **Exception contract** (intentional deviation from `DataResult` pattern):
     * Throws on failure; re-throws [kotlinx.coroutines.CancellationException] unchanged.
     * All callers must wrap this in try/catch and treat failures as best-effort.
     * The foreground-only constraint means callers already own a coroutine scope and
     * error-handling loop, so `DataResult` wrapping would add indirection without benefit.
     */
    suspend fun describe(bitmap: Bitmap): String

    /** Returns `true` if the on-device model is ready for inference. */
    suspend fun isModelDownloaded(): Boolean

    /**
     * Triggers download of the on-device model if not already available.
     *
     * **Exception contract**: throws [com.google.mlkit.genai.common.GenAiException] on
     * download failure. Callers must handle it; [kotlinx.coroutines.CancellationException]
     * is re-thrown unchanged. See [describe] for the rationale.
     */
    suspend fun ensureModelDownloaded()

    /**
     * In-flight progress for the current batch enrichment run.
     * `null` when idle; non-null while [startBatchEnrichment] is executing.
     */
    val progress: StateFlow<BatchCaptionProgress?>

    /**
     * Terminal result of the most recent batch enrichment run.
     * Set when the run finishes; cleared by [consumeResult].
     */
    val result: StateFlow<BatchCaptionResult?>

    /**
     * Launches a batch caption enrichment pass over all items that have an image but
     * no caption yet. Updates [progress] throughout and sets [result] on completion.
     * No-op if a run is already in progress.
     */
    fun startBatchEnrichment()

    /**
     * Signals that the UI has consumed the most recent batch-caption result.
     * Clears [result] so the same summary is not shown again after dismissal.
     */
    fun consumeResult()
}
