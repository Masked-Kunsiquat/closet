package com.closet.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val progress: StateFlow<BackupProgress> = BackupForegroundService.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupProgress.Idle)

    fun startExport(outputUri: Uri) {
        context.startForegroundService(
            Intent(context, BackupForegroundService::class.java).apply {
                action = BackupForegroundService.ACTION_EXPORT
                putExtra(BackupForegroundService.EXTRA_OUTPUT_URI, outputUri.toString())
            }
        )
    }

    fun startRestore(sourceUri: Uri) {
        context.startForegroundService(
            Intent(context, BackupForegroundService::class.java).apply {
                action = BackupForegroundService.ACTION_RESTORE
                putExtra(BackupForegroundService.EXTRA_SOURCE_URI, sourceUri.toString())
            }
        )
    }

    fun cancelOperation() {
        context.startService(
            Intent(context, BackupForegroundService::class.java).apply {
                action = BackupForegroundService.ACTION_CANCEL
            }
        )
    }

    /** Call after the UI has fully handled a terminal [BackupProgress.Success] or [BackupProgress.Error] state. */
    fun resetProgress() {
        BackupForegroundService.resetProgress()
    }
}
