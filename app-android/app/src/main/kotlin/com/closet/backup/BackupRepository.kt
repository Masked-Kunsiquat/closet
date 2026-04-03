package com.closet.backup

import android.content.Context
import android.net.Uri
import com.closet.BuildConfig
import com.closet.core.data.ClothingDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the backup export operation.
 *
 * Produces a `.hangr` ZIP archive containing:
 * - `closet.db`     — the Room database (WAL flushed before copy)
 * - `images/`       — flat copy of `closet_images/`
 * - `prefs/`        — the three DataStore `.preferences_pb` files, renamed to `.pb`
 * - `manifest.json` — schema version, app version, timestamp, image count
 *
 * API keys are device-locked via Android Keystore and are never exported
 * ([BackupManifest.apiKeysExcluded] is always `true`).
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ClothingDatabase,
    private val json: Json,
) {

    companion object {
        private const val DB_NAME = "closet.db"
        private const val IMAGES_DIR_NAME = "closet_images"
        private val PREF_STORE_NAMES = listOf("closet_prefs", "ai_prefs", "weather_prefs")
        private const val TEMP_DIR_NAME = "backup_temp"
        private const val TEMP_ZIP_NAME = "backup_temp.hangr"
        private const val TAG = "BackupRepository"
    }

    /**
     * Exports a complete backup, writing the resulting `.hangr` ZIP to [outputUri].
     *
     * Steps:
     * 1. WAL checkpoint — flush pending writes to the main DB file
     * 2. Copy `closet.db`
     * 3. Copy `closet_images/` → `images/`
     * 4. Copy DataStore preference files → `prefs/`
     * 5. Write `manifest.json`
     * 6. ZIP all of the above to a temp file
     * 7. Stream the ZIP to [outputUri] via SAF
     * 8. Delete temp files
     *
     * Progress is pushed via [onProgress]. Temp files are always cleaned up in the
     * `finally` block regardless of success or failure.
     */
    suspend fun export(outputUri: Uri, onProgress: (BackupProgress) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.filesDir, TEMP_DIR_NAME)
            val tempZip = File(context.filesDir, TEMP_ZIP_NAME)
            try {
                tempDir.deleteRecursively()
                tempZip.delete()
                tempDir.mkdirs()

                // 1. Flush WAL so closet.db is a consistent snapshot
                onProgress(BackupProgress.Running("Flushing database", 0, 0))
                val db = database.openHelper.writableDatabase
                db.execSQL("PRAGMA wal_checkpoint(FULL)")
                val schemaVersion = db.version

                // 2. Copy database file
                onProgress(BackupProgress.Running("Copying database", 0, 1))
                context.getDatabasePath(DB_NAME)
                    .copyTo(File(tempDir, DB_NAME), overwrite = true)
                onProgress(BackupProgress.Running("Copying database", 1, 1))

                // 3. Copy images
                val imagesSource = File(context.filesDir, IMAGES_DIR_NAME)
                val imagesDest = File(tempDir, "images").also { it.mkdirs() }
                val imageFiles = imagesSource.listFiles()?.filter { it.isFile } ?: emptyList()
                imageFiles.forEachIndexed { index, file ->
                    onProgress(BackupProgress.Running("Copying images", index, imageFiles.size))
                    file.copyTo(File(imagesDest, file.name), overwrite = true)
                }
                onProgress(BackupProgress.Running("Copying images", imageFiles.size, imageFiles.size))

                // 4. Copy DataStore preference files
                val datastoreDir = File(context.filesDir, "datastore")
                val prefsDest = File(tempDir, "prefs").also { it.mkdirs() }
                PREF_STORE_NAMES.forEachIndexed { index, name ->
                    onProgress(BackupProgress.Running("Copying preferences", index, PREF_STORE_NAMES.size))
                    val src = File(datastoreDir, "$name.preferences_pb")
                    if (src.exists()) {
                        src.copyTo(File(prefsDest, "$name.pb"), overwrite = true)
                    } else {
                        Timber.tag(TAG).w("Preference file not found: ${src.name}")
                    }
                }
                onProgress(BackupProgress.Running("Copying preferences", PREF_STORE_NAMES.size, PREF_STORE_NAMES.size))

                // 5. Write manifest.json
                val manifest = BackupManifest(
                    schemaVersion = schemaVersion,
                    appVersion = BuildConfig.VERSION_NAME,
                    createdAt = System.currentTimeMillis(),
                    imageCount = imageFiles.size,
                )
                File(tempDir, "manifest.json").writeText(json.encodeToString(manifest))

                // 6. ZIP the temp directory
                onProgress(BackupProgress.Running("Compressing backup", 0, 0))
                zipDirectory(tempDir, tempZip)

                // 7. Stream ZIP to SAF output URI
                onProgress(BackupProgress.Running("Saving backup", 0, 0))
                context.contentResolver.openOutputStream(outputUri)?.use { out ->
                    tempZip.inputStream().use { it.copyTo(out) }
                } ?: return@withContext Result.failure(Exception("Could not open output URI"))

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Export failed")
                Result.failure(e)
            } finally {
                tempDir.deleteRecursively()
                tempZip.delete()
            }
        }

    private fun zipDirectory(sourceDir: File, destZip: File) {
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }
    }
}
