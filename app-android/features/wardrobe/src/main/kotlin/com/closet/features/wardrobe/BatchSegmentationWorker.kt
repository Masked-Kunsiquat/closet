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
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.StorageRepository
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
    private val storageRepository: StorageRepository,
    private val segmentationRepository: SegmentationRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "batch_segmentation"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "segmentation_batch"
    }

    override suspend fun doWork(): Result {
        // FOSS builds do not include the ML Kit segmentation library; exit cleanly.
        if (!segmentationRepository.isSupported) return Result.success()

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
                        continue
                    }

                val masked = segmentationRepository.removeBackground(bitmap)
                val savedPath = storageRepository.saveBitmap(masked, "${UUID.randomUUID()}.png")
                clothingDao.updateItemImagePath(item.id, savedPath, Instant.now())

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

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(done: Int, total: Int): Notification {
        // TODO(Phase 6): on API 36+ replace with Notification.ProgressStyle for the
        //   standardised Live Update appearance in the system notification shade.
        //   ref: https://developer.android.com/develop/ui/views/notifications/live-update
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
        while (longest / (sampleSize * 2) >= maxDim) sampleSize *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }
}
