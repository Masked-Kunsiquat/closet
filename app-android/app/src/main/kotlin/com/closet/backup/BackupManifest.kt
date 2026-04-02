package com.closet.backup

import kotlinx.serialization.Serializable

/**
 * Written as `manifest.json` at the root of every `.hangr` ZIP.
 *
 * The restore flow reads this first to validate compatibility before touching any app data.
 */
@Serializable
data class BackupManifest(
    /** Room database schema version at backup time. Restore runs migrations if this is lower than the current version. */
    val schemaVersion: Int,
    /** App versionName (e.g. "0.2.0") at backup time. Informational only. */
    val appVersion: String,
    /** Unix epoch milliseconds when the backup was created. */
    val createdAt: Long,
    /** Number of image files written to the `images/` directory of the ZIP. */
    val imageCount: Int,
    /**
     * Always `true` — API keys are device-locked via Android Keystore and cannot be exported.
     * Checked on restore to trigger the "re-enter API keys" banner in Settings.
     */
    val apiKeysExcluded: Boolean = true,
)
