package com.closet.features.wardrobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.palette.graphics.Palette
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LookupDao
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.StorageRepository
import com.closet.core.data.util.ColorMatcher
import com.closet.core.data.worker.BatchSegmentationWork
import com.closet.features.wardrobe.repository.SegmentationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.Instant
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
@HiltWorker
class BatchSegmentationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val clothingDao: ClothingDao,
    private val lookupDao: LookupDao,
    private val storageRepository: StorageRepository,
    private val segmentationRepository: SegmentationRepository,
    private val clothingRepository: ClothingRepository,
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
            Timber.e(e, "BatchSeg: model download failed, aborting")
            return Result.failure(workDataOf("error" to "model_download_failed"))
        }

        val items = clothingDao.getItemsNeedingSegmentation()
        val total = items.size
        if (total == 0) return Result.success(workDataOf(KEY_DONE to 0, KEY_FAILED to 0))

        ensureNotificationChannel()
        setForeground(createForegroundInfo(0, total))

        var done = 0
        var failed = 0

        for (item in items) {
            try {
                val originalPath = item.imagePath ?: continue
                val file = storageRepository.getFile(originalPath)

                val bitmap = decodeSampledBitmap(file.absolutePath, maxDim = 1024)
                    ?: run {
                        Timber.w("BatchSeg: could not decode item ${item.id} ($originalPath)")
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
                    savedPath = storageRepository.saveBitmap(masked, "${UUID.randomUUID()}.png")
                    clothingDao.updateItemImagePath(item.id, savedPath, Instant.now())
                } catch (e: Exception) {
                    // Best-effort cleanup of the orphaned PNG before re-throwing so
                    // the outer catch can count this item as failed.
                    savedPath?.let { path ->
                        runCatching { storageRepository.deleteImage(path) }
                            .onFailure { Timber.d(it, "BatchSeg: failed to delete orphaned PNG $path") }
                    }
                    throw e
                }

                // Best-effort: re-extract colours from the masked bitmap and refresh the
                // semantic description. The Palette API skips transparent pixels so only
                // subject colours are sampled — not the removed background.
                // Runs inline (worker is already on IO); failure never marks the item as failed.
                try {
                    val palette = Palette.from(masked).generate()
                    val extracted = listOfNotNull(
                        palette.dominantSwatch?.rgb,
                        palette.vibrantSwatch?.rgb,
                        palette.mutedSwatch?.rgb,
                    ).distinct()
                    if (extracted.isNotEmpty()) {
                        val availableColors = lookupDao.getAllColors()
                        if (availableColors.isNotEmpty()) {
                            val matched = extracted
                                .map { ColorMatcher.findNearestColor(it, availableColors) }
                                .distinctBy { it.id }
                            clothingRepository.updateItemColors(item.id, matched.map { it.id })
                            Timber.d("BatchSeg: item ${item.id} colours → ${matched.joinToString { it.name }}")
                            clothingRepository.revectorizeItem(item.id)
                        }
                    } else {
                        Timber.d("BatchSeg: item ${item.id} — no swatches extracted from masked bitmap")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "BatchSeg: colour re-extraction failed for item ${item.id} — ignored")
                }

                // Delete the original file best-effort — a failure here is not fatal
                runCatching { storageRepository.deleteImage(originalPath) }
                    .onFailure { Timber.d(it, "BatchSeg: failed to delete original $originalPath") }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "BatchSeg: failed for item ${item.id}")
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
        val progress = if (total > 0) done * 10_000 / total else 0
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

    /**
     * Two-pass decode: first reads only the image bounds, then decodes at the largest
     * power-of-two [inSampleSize] that keeps the longest side ≤ [maxDim].
     */
    private fun decodeSampledBitmap(path: String, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val longest = maxOf(opts.outWidth, opts.outHeight)
        var sampleSize = 1
        while (longest / sampleSize > maxDim) sampleSize *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }
}
