package com.closet.backup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Route for the Backup & Restore screen. */
@Serializable
object BackupRoute

fun NavController.navigateToBackup() {
    navigate(BackupRoute)
}

fun NavGraphBuilder.backupScreen(
    onNavigateUp: () -> Unit,
) {
    composable<BackupRoute> {
        BackupScreen(onNavigateUp = onNavigateUp)
    }
}
