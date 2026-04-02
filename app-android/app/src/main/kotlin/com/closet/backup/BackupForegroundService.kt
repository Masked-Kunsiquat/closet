package com.closet.backup

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.closet.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

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
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXPORT -> {
                Timber.d("BackupForegroundService: export requested")
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.backup_notification_preparing_export)))
                // TODO 1.3: activeJob = serviceScope.launch { backupRepository.export(::reportProgress) }
            }
            ACTION_RESTORE -> {
                val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)?.toUri() ?: run {
                    Timber.w("BackupForegroundService: restore started without source URI")
                    _progress.value = BackupProgress.Error("No source file provided")
                    stopSelf()
                    return START_NOT_STICKY
                }
                Timber.d("BackupForegroundService: restore requested from $sourceUri")
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.backup_notification_preparing_restore)))
                // TODO 1.4: activeJob = serviceScope.launch { restoreRepository.restore(sourceUri, ::reportProgress) }
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
}
