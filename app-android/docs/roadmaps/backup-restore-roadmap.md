# Backup / Restore Roadmap

## What gets backed up

| Asset | Notes |
|-------|-------|
| `closet.db` | All user data — clothing, outfits, logs, embeddings |
| `closet_images/` | UUID-named JPG/PNG/WebP files, referenced by relative path in DB. New photos are JPEG; background-removed (segmented) images are WebP on API 30+ or PNG on API 26–29. Format/extension is determined by `StorageRepository.segmentedFormat()` — DB rows store the full filename so no mapping is needed on restore. |
| `closet_prefs.pb` | Accent, dynamic color |
| `ai_prefs.pb` | AI provider, models, style vibe |
| `weather_prefs.pb` | Weather service, unit |
| API keys | Device-locked via Android Keystore — **cannot** be exported; user must re-enter |

## ZIP format (`.hangr`)

```text
backup-2026-04-01/
  manifest.json     ← schema version, app version, timestamp, image count, api_keys_excluded: true
  closet.db
  images/           ← flat copy of closet_images/ (same filenames)
  prefs/
    closet_prefs.pb
    ai_prefs.pb
    weather_prefs.pb
```

## Decisions

| # | Decision |
|---|----------|
| Conflict on restore | Overwrite everything |
| Schema version mismatch | Run Room migrations on the restored DB before reopening |
| Image conflict | Overwrite — same UUID = same logical file |
| Progress / cancellation | Foreground service + notification |
| Restore safety | Require confirmation dialog |
| In-progress ZIP location | `filesDir/backup_temp/` — explicit cleanup after SAF handoff |

---

## Phase 1 — Local backup to file

### 1.1 Infrastructure
- [x] Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions to `AndroidManifest.xml`
- [x] Create notification channel `backup_restore` in `ClosetApp.onCreate()`
- [x] Create `BackupForegroundService` — starts in foreground with a persistent notification, exposes a `progress: StateFlow<BackupProgress>` via a bound service or broadcast, stops itself when work completes or is cancelled

### 1.2 Data model
- [x] Create `BackupManifest` data class (schema version, app version, timestamp, image count, `apiKeysExcluded: Boolean`)
- [x] Create `BackupProgress` sealed class: `Idle | Running(step, done, total) | Success(outputUri) | Error(message)`

### 1.3 Export — `BackupRepository.export()`
- [x] `PRAGMA wal_checkpoint(FULL)` on the Room DB before copying (flush WAL to main file)
- [x] Copy `closet.db` → `filesDir/backup_temp/closet.db`
- [x] Copy all files from `closet_images/` → `filesDir/backup_temp/images/`
- [x] Copy the three `.pb` DataStore files → `filesDir/backup_temp/prefs/`
- [x] Serialize and write `manifest.json`
- [x] ZIP `filesDir/backup_temp/` → `filesDir/backup_temp.hangr`
- [x] Hand the finished ZIP to the caller via SAF `ACTION_CREATE_DOCUMENT` (suggested filename `hangr-backup-<date>.hangr`)
- [x] Delete `filesDir/backup_temp/` and `backup_temp.hangr` after SAF write completes (or on cancellation)

### 1.4 Restore — `RestoreRepository.restore(uri)`
- [x] Open `.hangr` via SAF `ACTION_OPEN_DOCUMENT`, unzip to `filesDir/restore_temp/`
- [x] Read and validate `manifest.json` — reject if `schemaVersion > current DB version` (can't migrate forward)
- [x] Close the Room DB connection (`ClothingDatabase.closeAndReset()`)
- [x] Overwrite `closet.db` with the restored copy
- [x] Reopen Room DB and run any pending migrations if `schemaVersion < current`
- [x] Overwrite images in `closet_images/` from `restore_temp/images/` (full replacement)
- [x] Overwrite the three `.pb` pref files
- [x] Delete `filesDir/restore_temp/`
- [x] Emit success; show "API keys were not restored — re-enter in Settings" banner (UI in 1.5)

### 1.5 ViewModel + UI
- [x] Create `BackupViewModel` — exposes `BackupProgress` state, triggers export/restore via the foreground service
- [x] Add "Export backup" and "Restore from backup" rows to `SettingsScreen`
- [x] Export: launch `ACTION_CREATE_DOCUMENT` → on result start `BackupForegroundService` in export mode
- [x] Restore: launch `ACTION_OPEN_DOCUMENT` → show confirmation `AlertDialog` → on confirm start `BackupForegroundService` in restore mode
- [x] Show progress UI (linear progress bar + step label) while service is running
- [x] Show post-restore snackbar: "API keys were not restored — re-enter in Settings"

---

## Phase 2 — Android Auto Backup

- [x] Create `res/xml/backup_rules.xml` — include `closet.db`, `closet_images/`, the three `.pb` files; exclude `ai_keys_encrypted`
- [x] Wire `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (API 30 and below) in `AndroidManifest.xml`

> Auto Backup has a 25 MB cap on older Android — large wardrobes will overflow. Phase 1 remains the full backup path.

---

## Phase 3 — Scheduled auto-export (deferred)

Periodic WorkManager job that writes a `.hangr` to a user-chosen folder (persisted SAF URI). Rolling local backup without user intervention. Depends on Phase 1 infrastructure.

---

## Phase 4 — Image compression (storage optimisation)

### Why this matters

`StorageRepository.saveImage` currently does a raw byte copy — no resize, no re-encode. A modern phone photo is 3–8 MB; a segmented PNG (`saveBitmap`) adds another 1–4 MB. A wardrobe of 100 items with backgrounds removed can exceed 1 GB, burning through the 2 GB Auto Backup cap and bloating `.hangr` exports.

**Target:** cap new images at ~1600 px on the longest edge, JPEG 85 % quality. Expected saving: 75–85 % per photo (8 MB → ~1–2 MB). Segmented PNGs handled separately because they require alpha channel.

---

### 4.1 Compress incoming photos — `saveImage`

`StorageRepository.saveImage(uri)` currently streams bytes verbatim. Replace with a decode → scale → re-encode pipeline:

- [x] In `saveImage`, decode the URI into a `Bitmap` via `BitmapFactory.decodeStream` with `inJustDecodeBounds` first pass to get dimensions without full load
- [x] Compute `inSampleSize` so the decoded bitmap's longest edge is ≤ 1600 px (power-of-two downsampling is fast and handled by the decoder)
- [x] Decode at that sample size, then do a final `Bitmap.createScaledBitmap` if the long edge is still > 1600 px after sampling
- [x] Re-encode to JPEG at quality 85 into the destination file (keep `.jpg` extension and UUID filename — no DB migration needed)
- [x] Recycle the intermediate bitmap
- [x] Add a `StorageRepository.MAX_DIMENSION = 1600` and `JPEG_QUALITY = 85` companion constants so they're easy to tune

### 4.2 Compress segmented images — `saveBitmap`

`saveBitmap` saves a lossless PNG for background-removed items (requires alpha). PNG cannot be lossy; use WebP instead:

- [x] On API 30+: encode with `Bitmap.CompressFormat.WEBP_LOSSY` at quality 85 — supports alpha, ~50–70 % smaller than equivalent PNG
- [x] On API 26–29 (minSdk): fall back to PNG but apply the same 1600 px longest-edge cap (already ARGB_8888; large dimensions are the main cost driver)
- [x] Change output extension to `.webp` on API 30+ (the filename is a new UUID so no existing DB rows are affected)
- [x] Extract the format/extension decision into a private `segmentedFormat(): Pair<CompressFormat, String>` helper in `StorageRepository`

### 4.3 Migrate existing images (background WorkManager job)

New images will be compressed after 4.1/4.2, but existing users have a full library of uncompressed files. Migrate them in the background:

- [ ] Create `ImageCompressionWorker : CoroutineWorker` in `core/data/worker/`
- [ ] Query all `ClothingItem` rows with a non-null `imagePath` via `ClothingDao`
- [ ] For each image file: check if long edge > 1600 px or file size > 1.5 MB — skip if already small
- [ ] If compression is needed: compress to a temp file alongside the original → atomically rename to replace (same filename, so no DB update required)
- [ ] Track progress via `setProgress` so a future UI can show it; log a summary at the end
- [ ] Schedule once via `OneTimeWorkRequest` with `ExistingWorkPolicy.KEEP` on app start after a 30-second initial delay (idle + battery-not-low constraints)
- [ ] Register the worker in `DataModule` / Hilt the same way `EmbeddingWorker` is registered

### 4.4 Storage usage display (optional, deferred)

- [ ] Add a "Storage used" row to Settings showing total size of `closet_images/` (computed on a background coroutine, formatted as MB/GB)
- [ ] Show a "Compress existing images" manual trigger button that starts `ImageCompressionWorker` immediately — useful after migrating a large library

---

### Size estimate after compression

| Scenario | Before | After |
|---|---|---|
| 100 items, photo only | ~400 MB | ~100 MB |
| 100 items, photo + segmented | ~800 MB | ~150–200 MB |
| Auto Backup headroom (2 GB cap) | ~250 items | ~1 000+ items |

---
