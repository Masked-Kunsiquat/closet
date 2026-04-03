package com.closet.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.closet.R
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Pending restore URI — set when the user picks a file, cleared when the dialog
    // is dismissed (user cancels) or after the service starts (user confirms).
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // SAF launcher: export — user picks where to save the .hangr file
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.startExport(it) }
    }

    // SAF launcher: restore — user picks a .hangr file; shows confirm dialog before starting
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    // Restore confirm dialog
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startRestore(pendingRestoreUri!!)
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.backup_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.backup_confirm_cancel))
                }
            },
        )
    }

    // Restore success → non-dismissable restart dialog
    if (progress is BackupProgress.Success && (progress as BackupProgress.Success).outputUri == null) {
        AlertDialog(
            onDismissRequest = { /* non-dismissable — must restart */ },
            title = { Text(stringResource(R.string.backup_restart_title)) },
            text = { Text(stringResource(R.string.backup_restart_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetProgress()
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(context.packageName)
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                    if (intent != null) context.startActivity(intent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) {
                    Text(stringResource(R.string.backup_restart_ok))
                }
            },
        )
    }

    // Export success → snackbar then reset
    val exportSuccessMessage = stringResource(R.string.backup_success_export)
    LaunchedEffect(progress) {
        val p = progress
        if (p is BackupProgress.Success && p.outputUri != null) {
            snackbarHostState.showSnackbar(exportSuccessMessage)
            viewModel.resetProgress()
        }
        if (p is BackupProgress.Error) {
            snackbarHostState.showSnackbar(p.message)
            viewModel.resetProgress()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.backup_navigate_up))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Progress bar — shown while an operation is running
            if (progress is BackupProgress.Running) {
                val running = progress as BackupProgress.Running
                val hasCount = running.total > 0
                if (hasCount) {
                    LinearProgressIndicator(
                        progress = { running.done.toFloat() / running.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = running.step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            val isIdle = progress is BackupProgress.Idle

            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_summary)) },
                modifier = if (isIdle) Modifier.clickable {
                    val filename = "hangr-backup-${LocalDate.now()}.hangr"
                    exportLauncher.launch(filename)
                } else Modifier,
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore)) },
                supportingContent = { Text(stringResource(R.string.backup_restore_summary)) },
                modifier = if (isIdle) Modifier.clickable {
                    restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                } else Modifier,
            )
        }
    }
}
