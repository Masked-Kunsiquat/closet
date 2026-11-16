package com.closet.platform.gms.wardrobe

import android.content.Context
import android.graphics.Bitmap
import com.closet.core.data.ai.BatchCaptionProgress
import com.closet.core.data.ai.BatchCaptionResult
import com.closet.core.data.ai.OnDeviceAiException
import com.closet.platform.gms.mapGenAiErrorCode
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.CaptionEnrichmentProvider
import com.closet.core.data.util.BitmapUtils
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.time.Clock
import java.util.concurrent.ExecutionException
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
 * This is the full-flavor implementation, bound in [com.closet.platform.gms.di.gmsModule].
 * The FOSS stub is `UnsupportedImageCaptioner` in `features/wardrobe` `src/main`.
 */
class MlKitImageCaptioner(
    private val context: Context,
    private val clothingDao: ClothingDao,
) : CaptionEnrichmentProvider {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var enrichmentJob: Job? = null

    private val _progress = MutableStateFlow<BatchCaptionProgress?>(null)
    override val progress: StateFlow<BatchCaptionProgress?> = _progress.asStateFlow()

    private val _result = MutableStateFlow<BatchCaptionResult?>(null)
    override val result: StateFlow<BatchCaptionResult?> = _result.asStateFlow()

    /** `true` in the full flavor — used by the ViewModel to gate the caption path. */
    override val isSupported: Boolean = true

    /**
     * Generates a one-sentence description of [bitmap].
     *
     * Must be called from the main thread / a foreground Composable scope.
     *
     * @throws IllegalStateException if the API returns a null or empty description.
     */
    override suspend fun describe(bitmap: Bitmap): String {
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
    override suspend fun isModelDownloaded(): Boolean {
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
    override suspend fun ensureModelDownloaded() {
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

    /**
     * Launches a batch caption enrichment pass over all eligible items (image present,
     * no caption yet). Runs sequentially on the main thread — Image Description requires it.
     * No-op if a run is already in progress.
     */
    override fun startBatchEnrichment() {
        if (enrichmentJob?.isActive == true) return
        enrichmentJob = repositoryScope.launch {
            try {
                val items = clothingDao.getItemsNeedingCaption()
                if (items.isEmpty()) {
                    _result.value = BatchCaptionResult(done = 0, failed = 0)
                    return@launch
                }
                _progress.value = BatchCaptionProgress(done = 0, total = items.size, failed = 0)
                val imagesDir = File(context.filesDir, "closet_images")
                var done = 0
                var failed = 0
                var stoppedReason: String? = null
                for (item in items) {
                    val relativePath = item.imagePath ?: continue
                    try {
                        val absolutePath = File(imagesDir, relativePath).absolutePath
                        val bitmap = withContext(Dispatchers.IO) {
                            BitmapUtils.decodeSampledBitmap(absolutePath, maxDim = 512)
                        }
                        if (bitmap == null) {
                            Timber.w("Failed to decode bitmap for item ${item.id}")
                            failed++
                        } else {
                            val caption = describe(bitmap)
                            val updated = clothingDao.updateImageCaption(item.id, caption, Clock.System.now())
                            if (updated == 1) {
                                done++
                            } else {
                                Timber.w("Caption write skipped for item ${item.id} — item may have been deleted")
                                failed++
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason = e.stopReason()
                        if (reason != null) {
                            // OS-level block affects every remaining item — stop instead of failing them all.
                            Timber.w(e, "Caption batch stopped at item ${item.id}")
                            stoppedReason = reason.userMessage
                            break
                        }
                        Timber.w(e, "Caption failed for item ${item.id}")
                        failed++
                    }
                    _progress.value = BatchCaptionProgress(done = done, total = items.size, failed = failed)
                }
                _result.value = BatchCaptionResult(done = done, failed = failed, stoppedReason = stoppedReason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Batch caption enrichment failed")
            } finally {
                _progress.value = null
            }
        }
    }

    /** Clears the last result so the UI does not show the same summary twice. */
    override fun consumeResult() {
        _result.value = null
    }
}

/**
 * The [OnDeviceAiException.Reason] if this failure (or its [ExecutionException] cause) means the
 * whole batch should stop: on-device AI blocked in the background, or the daily quota is spent.
 */
private fun Throwable.stopReason(): OnDeviceAiException.Reason? {
    val genAi = (this as? GenAiException) ?: (cause as? GenAiException) ?: return null
    return when (val reason = mapGenAiErrorCode(genAi.errorCode)) {
        OnDeviceAiException.Reason.BACKGROUND_BLOCKED,
        OnDeviceAiException.Reason.QUOTA_EXCEEDED -> reason
        else -> null
    }
}
