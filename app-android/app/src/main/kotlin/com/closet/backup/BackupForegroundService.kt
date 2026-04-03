package com.closet.backup

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.closet.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that drives backup export and restore operations.
 *
 * Runs in the foreground so the OS cannot kill it mid-operation on large wardrobes.
 * Progress is exposed via [progress], a static [StateFlow] observed by `BackupViewModel`.
 *
 * Start with one of the [ACTION_EXPORT], [ACTION_RESTORE], or [ACTION_CANCEL] intent actions.
 * The service stops itself when the operation completes, fails, or is cancelled.
 */
@AndroidEntryPoint
class BackupForegroundService : Service() {

    companion object {
        /** Start an export. No extras required — SAF file picker is launched from the UI beforehand. */
        const val ACTION_EXPORT = "com.closet.backup.ACTION_EXPORT"

        /** Start a restore. Requires [EXTRA_SOURCE_URI] pointing to the `.hangr` file. */
        const val ACTION_RESTORE = "com.closet.backup.ACTION_RESTORE"

        /** Cancel any running operation and stop the service. */
        const val ACTION_CANCEL = "com.closet.backup.ACTION_CANCEL"

        /** String extra: SAF URI of the `.hangr` file to write. Required for [ACTION_EXPORT]. */
        const val EXTRA_OUTPUT_URI = "output_uri"

        /** String extra: URI of the `.hangr` file to restore from. Required for [ACTION_RESTORE]. */
        const val EXTRA_SOURCE_URI = "source_uri"

        /** Notification channel ID — registered in [com.closet.ClosetApp.onCreate]. */
        const val CHANNEL_ID = "backup_restore"

        private const val NOTIFICATION_ID = 2001

        private val _progress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)

        /**
         * Live progress for the current operation. Observed by `BackupViewModel`.
         * Resets to [BackupProgress.Idle] when the service is destroyed mid-run.
         */
        val progress: StateFlow<BackupProgress> = _progress.asStateFlow()

        /**
         * Called by [BackupViewModel] after the UI has handled a terminal [BackupProgress] state.
         * No-ops if an operation is currently [BackupProgress.Running] to avoid clobbering
         * in-flight progress.
         */
        fun resetProgress() {
            if (_progress.value !is BackupProgress.Running) {
                _progress.value = BackupProgress.Idle
            }
        }
    }

    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var restoreRepository: RestoreRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXPORT -> {
                val outputUri = intent.getStringExtra(EXTRA_OUTPUT_URI)?.toUri() ?: run {
                    Timber.w("BackupForegroundService: export started without output URI")
                    _progress.value = BackupProgress.Error("No output file provided")
                    stopSelf()
                    return START_NOT_STICKY
                }
                Timber.d("BackupForegroundService: export requested to $outputUri")
                startForegroundCompat(buildNotification(getString(R.string.backup_notification_preparing_export)))
                activeJob = serviceScope.launch {
                    try {
                        backupRepository.export(outputUri, ::reportProgress)
                            .onSuccess { reportProgress(BackupProgress.Success(outputUri)) }
                            .onFailure { e -> reportProgress(BackupProgress.Error(e.message ?: "Export failed")) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Unexpected export error")
                        reportProgress(BackupProgress.Error(e.message ?: "Export failed"))
                    }
                }
            }
            ACTION_RESTORE -> {
                val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)?.toUri() ?: run {
                    Timber.w("BackupForegroundService: restore started without source URI")
                    _progress.value = BackupProgress.Error("No source file provided")
                    stopSelf()
                    return START_NOT_STICKY
                }
                Timber.d("BackupForegroundService: restore requested from $sourceUri")
                startForegroundCompat(buildNotification(getString(R.string.backup_notification_preparing_restore)))
                activeJob = serviceScope.launch {
                    try {
                        restoreRepository.restore(sourceUri, ::reportProgress)
                            .onSuccess { reportProgress(BackupProgress.Success()) }
                            .onFailure { e -> reportProgress(BackupProgress.Error(e.message ?: "Restore failed")) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Unexpected restore error")
                        reportProgress(BackupProgress.Error(e.message ?: "Restore failed"))
                    }
                }
            }
            ACTION_CANCEL -> {
                Timber.d("BackupForegroundService: cancellation requested")
                activeJob?.cancel()
                stopSelf()
            }
            else -> {
                Timber.w("BackupForegroundService: unknown action ${intent?.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activeJob?.cancel()
        serviceScope.cancel()
        // If destroyed mid-run (e.g. system kill), reset so the UI doesn't stay stuck on Running.
        if (_progress.value is BackupProgress.Running) {
            _progress.value = BackupProgress.Idle
        }
    }

    /**
     * Called by [BackupRepository] / [RestoreRepository] coroutines (wired in 1.3 / 1.4)
     * to push progress updates to both the UI flow and the persistent notification.
     */
    internal fun reportProgress(progress: BackupProgress) {
        _progress.value = progress
        if (progress is BackupProgress.Running) {
            updateNotification("${progress.step} (${progress.done}/${progress.total})")
        }
        if (progress is BackupProgress.Success || progress is BackupProgress.Error) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * Calls the three-argument [startForeground] overload (API 29+) passing
     * [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] to comply with the
     * `android:foregroundServiceType="dataSync"` manifest declaration required on API 34+.
     * Falls back to the two-argument overload on API 26–28.
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
