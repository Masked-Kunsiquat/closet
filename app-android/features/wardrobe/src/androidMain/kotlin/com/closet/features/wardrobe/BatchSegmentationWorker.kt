package com.closet.features.wardrobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.color.ColorExtractor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.dao.LookupDao
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.time.AppClock
import com.closet.core.data.util.BitmapUtils
import com.closet.core.data.util.DataResult
import com.closet.core.data.util.toArgbPixels
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.features.wardrobe.repository.SubjectSegmenter
import kotlinx.coroutines.CancellationException
import co.touchlab.kermit.Logger
import java.util.UUID

/**
 * Background worker that removes the background from all wardrobe items whose stored image is
 * not yet a PNG (i.e., have not been segmented). Runs as a foreground service so it survives
 * the app being killed; progress is reported via [setProgress] and a persistent notification.
 *
 * Enqueue with [WORK_NAME] as a unique work name to prevent duplicate runs:
 * ```
 * WorkManager.getInstance(context).enqueueUniqueWork(
 *     BatchSegmentationWorker.WORK_NAME,
 *     ExistingWorkPolicy.KEEP,
 *     OneTimeWorkRequestBuilder<BatchSegmentationWorker>().build()
 * )
 * ```
 *
 * On completion, the output [androidx.work.Data] contains [KEY_DONE] and [KEY_FAILED] counts.
 */
private val log = Logger.withTag("BatchSegmentationWorker")

class BatchSegmentationWorker(
    private val context: Context,
    params: WorkerParameters,
    private val clothingDao: ClothingDao,
    private val lookupDao: LookupDao,
    private val storageRepository: StorageRepository,
    private val segmentationRepository: SubjectSegmenter,
    private val clothingRepository: ClothingRepository,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {

    companion object {
        val WORK_NAME = BatchSegmentationWork.NAME
        val KEY_DONE = BatchSegmentationWork.KEY_DONE
        val KEY_TOTAL = BatchSegmentationWork.KEY_TOTAL
        val KEY_FAILED = BatchSegmentationWork.KEY_FAILED

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "segmentation_batch"
    }

    override suspend fun doWork(): Result {
        // FOSS builds do not include the ML Kit segmentation library; exit cleanly.
        if (!segmentationRepository.isSupported) return Result.success()

        // Ensure the model is available before starting the foreground service + notification.
        // If GMS cannot deliver the model (e.g. Play Services degraded, no network on first run),
        // fail fast so the user sees a clear failure rather than every item silently failing.
        try {
            segmentationRepository.ensureModelDownloaded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("BatchSeg: model download failed, aborting", e)
            return Result.failure(workDataOf("error" to "model_download_failed"))
        }

        val items = clothingDao.getItemsNeedingSegmentation()
        val total = items.size
        if (total == 0) return Result.success(workDataOf(KEY_DONE to 0, KEY_FAILED to 0))

        ensureNotificationChannel()
        setForeground(createForegroundInfo(0, total))

        // Loaded once per run. A failed load only disables colour re-extraction, never segmentation.
        val extractor: ColorExtractor? = try {
            ColorExtractor(lookupDao.getAllColorShades().map { ReferenceShade(it.colorId, it.hex) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w("BatchSeg: reference shades failed to load; colours will not be re-extracted", e)
            null
        }

        var done = 0
        var failed = 0

        for (item in items) {
            try {
                val originalPath = item.imagePath ?: continue
                val file = storageRepository.getFile(originalPath)

                val bitmap = BitmapUtils.decodeSampledBitmap(file.absolutePath, maxDim = 1024)
                    ?: run {
                        log.w("BatchSeg: could not decode item ${item.id} ($originalPath)")
                        failed++
                        done++
                        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                        setForeground(createForegroundInfo(done, total))
                        continue
                    }

                val masked = segmentationRepository.removeBackground(bitmap)
                // Declare outside try so the failure branch can clean up if the DAO
                // write fails after the PNG has already been saved to disk.
                var savedPath: String? = null
                try {
                    savedPath = storageRepository.saveBitmap(masked, UUID.randomUUID().toString())
                    clothingDao.updateItemImagePath(item.id, savedPath, clock.now())
                } catch (e: Exception) {
                    // Best-effort cleanup of the orphaned PNG before re-throwing so
                    // the outer catch can count this item as failed.
                    savedPath?.let { path ->
                        runCatching { storageRepository.deleteImage(path) }
                            .onFailure { log.d("BatchSeg: failed to delete orphaned PNG $path", it) }
                    }
                    throw e
                }

                // Best-effort: re-measure colours from the masked bitmap (alpha is the mask, so
                // only subject pixels count, not the removed background) and refresh the semantic
                // description. Writes only through replaceAutoColors, which leaves user-curated
                // rows (and empty extractions) alone; a false result is expected, not an error.
                // Runs inline (worker is already on IO); failure never marks the item as failed.
                try {
                    if (extractor != null) {
                        val extracted = extractor.extract(masked.toArgbPixels(), masked.width, masked.height)
                        val replaced = clothingRepository.replaceAutoColors(item.id, extracted)
                        if (replaced is DataResult.Success && replaced.data) {
                            log.d("BatchSeg: item ${item.id} colours → ${extracted.joinToString { "${it.colorId}:${it.hex}" }}")
                            clothingRepository.revectorizeItem(item.id)
                        } else if (replaced is DataResult.Error) {
                            log.w("BatchSeg: colour write failed for item ${item.id} — ignored", replaced.throwable)
                        } else {
                            log.d("BatchSeg: item ${item.id} colours left unchanged (user-curated or none extracted)")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.w("BatchSeg: colour re-extraction failed for item ${item.id} — ignored", e)
                }

                // Delete the original file best-effort — a failure here is not fatal
                runCatching { storageRepository.deleteImage(originalPath) }
                    .onFailure { log.d("BatchSeg: failed to delete original $originalPath", it) }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("BatchSeg: failed for item ${item.id}", e)
                failed++
            }

            done++
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            setForeground(createForegroundInfo(done, total))
        }

        return Result.success(workDataOf(KEY_DONE to done, KEY_FAILED to failed))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background removal",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // dataSync is declared on SystemForegroundService in AndroidManifest.xml via tools:node="merge";
    // WorkManager lint does not resolve merged manifests in library modules, so suppress the false positive.
    @android.annotation.SuppressLint("SpecifyForegroundServiceType")
    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(done: Int, total: Int): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildLiveUpdateNotification(done, total)
        } else {
            buildLegacyNotification(done, total)
        }
    }

    /**
     * API 36+ (Android 16): uses [Notification.ProgressStyle] for the standardised
     * Live Update appearance in the system notification shade.
     * ref: https://developer.android.com/develop/ui/views/notifications/live-update
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveUpdateNotification(done: Int, total: Int): Notification {
        val progress = liveUpdateProgress(done, total)
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Removing backgrounds")
            .setContentText("$done of $total items")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setStyle(
                Notification.ProgressStyle()
                    .setProgress(progress)
                    .setProgressIndeterminate(total == 0)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** API < 36 fallback: standard determinate progress bar in the notification shade. */
    private fun buildLegacyNotification(done: Int, total: Int): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Removing backgrounds")
            .setContentText("$done of $total items")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(total, done, /* indeterminate= */ false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
