package com.closet.core.data.repository

import android.graphics.Bitmap

/**
 * Abstraction over the ML Kit GenAI Image Description API. Common code (e.g. `features/settings`)
 * uses the batch half, [BatchCaptioner]; this Android interface adds the bitmap-level calls used
 * by `features/wardrobe`.
 *
 * The full-flavor implementation is `com.closet.platform.gms.wardrobe.MlKitImageCaptioner`.
 * The FOSS-flavor stub (`UnsupportedImageCaptioner` in `features/wardrobe`) sets [isSupported] = `false` and throws on [describe].
 *
 * Provided by `wardrobeNoOpModule` (foss) or `gmsModule` in `platform/gms` (full), following the same
 * pattern as [BatchSegmentationScheduler].
 */
interface CaptionEnrichmentProvider : BatchCaptioner {

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
}
