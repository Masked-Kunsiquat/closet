package com.closet.features.wardrobe.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps the ML Kit GenAI Image Description API to produce a one-sentence photo caption.
 *
 * Example output: "A white mesh sneaker photographed against a clean background."
 *
 * **Foreground-only constraint.** The Image Description API requires a live UI context
 * and must be called from the main thread. All callers must dispatch on [kotlinx.coroutines.Dispatchers.Main].
 *
 * This is the full-flavor implementation. The FOSS stub lives in `src/foss/`.
 */
@Singleton
class ImageCaptionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** `true` in the full flavor — used by the ViewModel to gate the caption path. */
    val isSupported: Boolean = true

    /**
     * Generates a one-sentence description of [bitmap].
     *
     * Must be called from the main thread / a foreground Composable scope.
     *
     * @throws IllegalStateException if the API returns a null or empty description.
     */
    suspend fun describe(bitmap: Bitmap): String {
        val options = ImageDescriberOptions.builder(context).build()
        val describer = ImageDescription.getClient(options)
        return try {
            val request = ImageDescriptionRequest.builder(bitmap).build()
            val result = suspendCancellableCoroutine { cont ->
                val future = describer.runInference(request)
                future.addListener({
                    try {
                        cont.resume(future.get())
                    } catch (e: ExecutionException) {
                        cont.resumeWithException(e.cause ?: e)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, { it.run() })
                cont.invokeOnCancellation { future.cancel(true) }
            }
            result.description?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("ImageDescriber returned a null or empty description")
        } finally {
            describer.close()
        }
    }

    /**
     * Returns `true` if the on-device Image Description model is downloaded and ready.
     */
    suspend fun isModelDownloaded(): Boolean {
        val options = ImageDescriberOptions.builder(context).build()
        val describer = ImageDescription.getClient(options)
        return try {
            suspendCancellableCoroutine { cont ->
                val future = describer.checkFeatureStatus()
                future.addListener({
                    try {
                        cont.resume(future.get() == FeatureStatus.AVAILABLE)
                    } catch (_: Exception) {
                        cont.resume(false)
                    }
                }, { it.run() })
                cont.invokeOnCancellation { future.cancel(true) }
            }
        } catch (_: Exception) {
            false
        } finally {
            describer.close()
        }
    }

    /**
     * Triggers model download if not already available. Suspends until the download
     * completes or throws if the device does not support the feature.
     */
    suspend fun ensureModelDownloaded() {
        val options = ImageDescriberOptions.builder(context).build()
        val describer = ImageDescription.getClient(options)
        try {
            suspendCancellableCoroutine { cont ->
                val future = describer.downloadFeature(object : DownloadCallback {
                    override fun onDownloadCompleted() { cont.resume(Unit) }
                    override fun onDownloadFailed(e: GenAiException) { cont.resumeWithException(e) }
                    override fun onDownloadProgress(bytesDownloaded: Long) { /* progress ignored */ }
                    override fun onDownloadStarted(totalBytes: Long) { /* ignored */ }
                })
                cont.invokeOnCancellation { future.cancel(true) }
            }
        } finally {
            describer.close()
        }
    }
}
