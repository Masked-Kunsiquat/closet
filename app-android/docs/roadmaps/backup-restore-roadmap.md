# Backup / Restore Roadmap

## What gets backed up

| Asset | Notes |
|-------|-------|
| `closet.db` | All user data — clothing, outfits, logs, embeddings |
| `closet_images/` | UUID-named JPG/PNG files, referenced by relative path in DB |
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
- [ ] Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions to `AndroidManifest.xml`
- [ ] Create notification channel `backup_restore` in `ClosetApp.onCreate()`
- [ ] Create `BackupForegroundService` — starts in foreground with a persistent notification, exposes a `progress: StateFlow<BackupProgress>` via a bound service or broadcast, stops itself when work completes or is cancelled

### 1.2 Data model
- [ ] Create `BackupManifest` data class (schema version, app version, timestamp, image count, `apiKeysExcluded: Boolean`)
- [ ] Create `BackupProgress` sealed class: `Idle | Running(step, done, total) | Success(outputUri) | Error(message)`

### 1.3 Export — `BackupRepository.export()`
- [ ] `PRAGMA wal_checkpoint(FULL)` on the Room DB before copying (flush WAL to main file)
- [ ] Copy `closet.db` → `filesDir/backup_temp/closet.db`
- [ ] Copy all files from `closet_images/` → `filesDir/backup_temp/images/`
- [ ] Copy the three `.pb` DataStore files → `filesDir/backup_temp/prefs/`
- [ ] Serialize and write `manifest.json`
- [ ] ZIP `filesDir/backup_temp/` → `filesDir/backup_temp.hangr`
- [ ] Hand the finished ZIP to the caller via SAF `ACTION_CREATE_DOCUMENT` (suggested filename `hangr-backup-<date>.hangr`)
- [ ] Delete `filesDir/backup_temp/` and `backup_temp.hangr` after SAF write completes (or on cancellation)

### 1.4 Restore — `RestoreRepository.restore(uri)`
- [ ] Open `.hangr` via SAF `ACTION_OPEN_DOCUMENT`, unzip to `filesDir/restore_temp/`
- [ ] Read and validate `manifest.json` — reject if `schemaVersion > current DB version` (can't migrate forward)
- [ ] Close the Room DB connection (`ClothingDatabase.close()`)
- [ ] Overwrite `closet.db` with the restored copy
- [ ] Reopen Room DB and run any pending migrations if `schemaVersion < current`
- [ ] Overwrite images in `closet_images/` from `restore_temp/images/` (overwrite on conflict)
- [ ] Overwrite the three `.pb` pref files
- [ ] Delete `filesDir/restore_temp/`
- [ ] Emit success; show "API keys were not restored — re-enter in Settings" banner

### 1.5 ViewModel + UI
- [ ] Create `BackupViewModel` — exposes `BackupProgress` state, triggers export/restore via the foreground service
- [ ] Add "Export backup" and "Restore from backup" rows to `SettingsScreen`
- [ ] Export: launch `ACTION_CREATE_DOCUMENT` → on result start `BackupForegroundService` in export mode
- [ ] Restore: launch `ACTION_OPEN_DOCUMENT` → show confirmation `AlertDialog` → on confirm start `BackupForegroundService` in restore mode
- [ ] Show progress UI (linear progress bar + step label) while service is running
- [ ] Show post-restore snackbar: "API keys were not restored — re-enter in Settings"

---

## Phase 2 — Android Auto Backup

- [ ] Create `res/xml/backup_rules.xml` — include `closet.db`, `closet_images/`, the three `.pb` files; exclude `ai_keys_encrypted`
- [ ] Wire `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (API 30 and below) in `AndroidManifest.xml`

> Auto Backup has a 25 MB cap on older Android — large wardrobes will overflow. Phase 1 remains the full backup path.

---

## Phase 3 — Scheduled auto-export (deferred)

Periodic WorkManager job that writes a `.hangr` to a user-chosen folder (persisted SAF URI). Rolling local backup without user intervention. Depends on Phase 1 infrastructure.

---
