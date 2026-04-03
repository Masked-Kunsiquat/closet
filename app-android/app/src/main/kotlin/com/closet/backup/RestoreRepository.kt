package com.closet.backup

import android.content.Context
import android.net.Uri
import com.closet.core.data.ClothingDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the restore operation from a `.hangr` backup archive.
 *
 * Restore order:
 * 1. Unzip the archive to `filesDir/restore_temp/`
 * 2. Read and validate `manifest.json` (reject if schema version is newer than the app)
 * 3. Close Room DB and delete WAL sidecar files
 * 4. Overwrite `closet.db` with the restored copy
 * 5. Reopen Room DB — triggers any pending migrations automatically
 * 6. Replace `closet_images/` with the restored images (full replacement)
 * 7. Overwrite DataStore preference files
 * 8. Delete `filesDir/restore_temp/`
 * 9. Return [kotlin.Result] success — caller must restart the process for the refreshed DB and
 *    DataStore instances to take effect across all Hilt-injected singletons
 *
 * API keys are device-locked and are never in the backup; the "re-enter keys" banner is
 * shown by the UI layer after a successful restore.
 */
@Singleton
class RestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ClothingDatabase,
    private val json: Json,
) {

    companion object {
        private const val DB_NAME = "closet.db"
        private const val IMAGES_DIR_NAME = "closet_images"
        private val PREF_STORE_NAMES = listOf("closet_prefs", "ai_prefs", "weather_prefs")
        private const val TEMP_DIR_NAME = "restore_temp"
        private const val TAG = "RestoreRepository"
    }

    /**
     * Restores a complete backup from [sourceUri].
     *
     * @param sourceUri SAF URI of the `.hangr` ZIP to restore from.
     * @param onProgress Progress callback — called on the IO dispatcher.
     * @return [Result.success] on completion. Caller is responsible for restarting the process.
     */
    suspend fun restore(sourceUri: Uri, onProgress: (BackupProgress) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.filesDir, TEMP_DIR_NAME)
            try {
                tempDir.deleteRecursively()
                tempDir.mkdirs()

                // 1. Unzip archive to restore_temp/
                onProgress(BackupProgress.Running("Unpacking backup", 0, 0))
                unzip(sourceUri, tempDir)

                // 2. Validate manifest
                onProgress(BackupProgress.Running("Validating backup", 0, 0))
                val manifestFile = File(tempDir, "manifest.json")
                if (!manifestFile.exists()) {
                    return@withContext Result.failure(Exception("Invalid backup: manifest.json not found"))
                }
                val manifest = json.decodeFromString<BackupManifest>(manifestFile.readText())
                val currentVersion = database.openHelper.readableDatabase.version
                if (manifest.schemaVersion > currentVersion) {
                    return@withContext Result.failure(
                        Exception(
                            "Backup was created with a newer version of the app " +
                                "(backup schema ${manifest.schemaVersion}, app schema $currentVersion). " +
                                "Update the app to restore this backup."
                        )
                    )
                }

                // 3. Close Room DB and remove WAL sidecar files
                onProgress(BackupProgress.Running("Closing database", 0, 0))
                ClothingDatabase.closeAndReset()
                val dbFile = context.getDatabasePath(DB_NAME)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()

                // 4. Overwrite closet.db
                onProgress(BackupProgress.Running("Restoring database", 0, 1))
                val restoredDb = File(tempDir, DB_NAME)
                if (!restoredDb.exists()) {
                    return@withContext Result.failure(Exception("Invalid backup: closet.db not found"))
                }
                dbFile.parentFile?.mkdirs()
                restoredDb.copyTo(dbFile, overwrite = true)
                onProgress(BackupProgress.Running("Restoring database", 1, 1))

                // 5. Reopen DB — Room runs any pending migrations automatically
                onProgress(BackupProgress.Running("Running migrations", 0, 0))
                ClothingDatabase.getDatabase(context)

                // 6. Replace images (full replacement — DB and images must stay in sync)
                val imagesSrc = File(tempDir, "images")
                val imagesDest = File(context.filesDir, IMAGES_DIR_NAME)
                imagesDest.deleteRecursively()
                imagesDest.mkdirs()
                val imageFiles = imagesSrc.listFiles()?.filter { it.isFile } ?: emptyList()
                imageFiles.forEachIndexed { index, file ->
                    onProgress(BackupProgress.Running("Restoring images", index, imageFiles.size))
                    file.copyTo(File(imagesDest, file.name), overwrite = true)
                }
                onProgress(BackupProgress.Running("Restoring images", imageFiles.size, imageFiles.size))

                // 7. Overwrite DataStore preference files.
                // Note: copying .preferences_pb files on disk does NOT invalidate the in-memory
                // DataStore caches held by Hilt singletons (closet_prefs, ai_prefs, weather_prefs).
                // A full process restart is required for the restored prefs to take effect —
                // BackupScreen shows a non-dismissable restart dialog after this step completes.
                val datastoreDir = File(context.filesDir, "datastore").also { it.mkdirs() }
                val prefsSrc = File(tempDir, "prefs")
                PREF_STORE_NAMES.forEach { name ->
                    val src = File(prefsSrc, "$name.pb")
                    if (src.exists()) {
                        src.copyTo(File(datastoreDir, "$name.preferences_pb"), overwrite = true)
                    } else {
                        Timber.tag(TAG).w("Preference file not in backup: $name.pb")
                    }
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Restore failed")
                Result.failure(e)
            } finally {
                tempDir.deleteRecursively()
            }
        }

    /**
     * Unzips [sourceUri] into [destDir]. Uses canonical-path comparison to ensure every
     * extracted file lands inside [destDir], guarding against path-traversal attacks
     * regardless of `..` encoding or leading separators in the entry name.
     */
    private fun unzip(sourceUri: Uri, destDir: File) {
        val destDirCanonical = destDir.canonicalFile
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // Strip any leading separators so File(destDir, name) always resolves
                    // relative to destDir, even for entries like "/etc/passwd".
                    val sanitizedName = entry.name.trimStart('/', '\\')
                    val outFile = File(destDir, sanitizedName)
                    val outFileCanonical = outFile.canonicalFile
                    val isInsideDest = outFileCanonical.path.startsWith(
                        destDirCanonical.path + File.separator
                    ) || outFileCanonical.path == destDirCanonical.path
                    if (!isInsideDest) {
                        Timber.tag(TAG).w("Skipping path-traversal zip entry: ${entry.name}")
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw Exception("Could not open backup file")
    }
}
