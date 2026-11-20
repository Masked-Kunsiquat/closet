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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.util.DataResult
import com.closet.core.data.worker.ColorRedetectWork
import kotlinx.coroutines.CancellationException

private val log = Logger.withTag("ColorRedetectWorker")

/**
 * Bulk colour re-detect (#128): measures every queued item's colours again from its photo and
 * writes them as auto colours, clearing the curated flag. Needs no Play Services, so it runs on
 * both flavors. Runs as a foreground service with a progress notification, like
 * [BatchSegmentationWorker].
 *
 * Input: [ColorRedetectWork.KEY_ONLY_UNCURATED] (default true) limits the queue to items whose
 * colours the user hasn't edited. Each item ends as re-detected ([ColorRedetectWork.KEY_DONE]),
 * unchanged because nothing was detected ([ColorRedetectWork.KEY_UNCHANGED]) or failed
 * ([ColorRedetectWork.KEY_FAILED]); progress and output carry all three plus
 * [ColorRedetectWork.KEY_TOTAL].
 */
class ColorRedetectWorker(
    private val context: Context,
    params: WorkerParameters,
    private val clothingDao: ClothingDao,
    private val colorRedetector: ColorRedetector,
    private val clothingRepository: ClothingRepository,
) : CoroutineWorker(context, params) {

    private companion object {
        const val NOTIFICATION_ID = 2003
        const val CHANNEL_ID = "color_redetect"
    }

    override suspend fun doWork(): Result {
        val onlyUncurated = inputData.getBoolean(ColorRedetectWork.KEY_ONLY_UNCURATED, true)
        val items = clothingDao.getColorRedetectItems(onlyUncurated)
        val total = items.size
        if (total == 0) return Result.success(counts(done = 0, unchanged = 0, failed = 0, total = 0))

        ensureNotificationChannel()
        setForeground(createForegroundInfo(0, total))

        var done = 0
        var unchanged = 0
        var failed = 0

        for (item in items) {
            try {
                val proposed = colorRedetector.propose(item.imagePath)
                if (proposed.isEmpty()) {
                    unchanged++
                } else {
                    // In only-unedited mode the write re-checks colors_curated in its transaction,
                    // so an item the user edits mid-run is left alone (counted unchanged).
                    when (val result = colorRedetector.apply(item.id, proposed, onlyIfUncurated = onlyUncurated)) {
                        is DataResult.Success -> if (result.data) {
                            done++
                            clothingRepository.revectorizeItem(item.id)
                        } else {
                            unchanged++
                        }
                        is DataResult.Error -> {
                            log.w(result.throwable) { "colour write failed for item ${item.id}" }
                            failed++
                        }
                        DataResult.Loading -> unchanged++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "colour re-detect failed for item ${item.id}" }
                failed++
            }

            val processed = done + unchanged + failed
            setProgress(counts(done, unchanged, failed, total))
            setForeground(createForegroundInfo(processed, total))
        }

        return Result.success(counts(done, unchanged, failed, total))
    }

    private fun counts(done: Int, unchanged: Int, failed: Int, total: Int): Data = workDataOf(
        ColorRedetectWork.KEY_DONE to done,
        ColorRedetectWork.KEY_UNCHANGED to unchanged,
        ColorRedetectWork.KEY_FAILED to failed,
        ColorRedetectWork.KEY_TOTAL to total,
    )

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Color re-detection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    // dataSync is declared on SystemForegroundService in AndroidManifest.xml via tools:node="merge";
    // WorkManager lint does not resolve merged manifests in library modules, so suppress the false positive.
    @android.annotation.SuppressLint("SpecifyForegroundServiceType")
    private fun createForegroundInfo(processed: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(processed, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(processed: Int, total: Int): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildLiveUpdateNotification(processed, total)
        } else {
            buildLegacyNotification(processed, total)
        }

    /** API 36+: [Notification.ProgressStyle] Live Update, as in [BatchSegmentationWorker]. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveUpdateNotification(processed: Int, total: Int): Notification {
        val progress = if (total > 0) processed * 10_000 / total else 0
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Re-detecting colors")
            .setContentText("$processed of $total items")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setStyle(
                Notification.ProgressStyle()
                    .setProgress(progress)
                    .setProgressIndeterminate(total == 0),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** API < 36 fallback: a determinate progress bar. */
    private fun buildLegacyNotification(processed: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Re-detecting colors")
            .setContentText("$processed of $total items")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(total, processed, /* indeterminate= */ false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}
