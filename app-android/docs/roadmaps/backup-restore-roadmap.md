# Backup / Restore Roadmap

## What gets backed up

| Asset | Notes |
|---|---|
| `closet.db` | All user data — clothing, outfits, logs, embeddings |
| `closet_images/` | UUID-named JPG/PNG files, referenced by relative path in DB |
| `closet_prefs.pb` | Accent, dynamic color |
| `ai_prefs.pb` | AI provider, models, style vibe |
| `weather_prefs.pb` | Weather service, unit |
| API keys | Device-locked via Android Keystore — **cannot** be exported; user must re-enter |

---

## Phase 1 — Local backup to file (ZIP export/import)

**Format:** A single `.hangr` ZIP:
```text
backup-2026-04-01/
  manifest.json          ← schema version, app version, timestamp, image count
  closet.db
  images/                ← flat copy of closet_images/ (same filenames)
  prefs/
    closet_prefs.pb
    ai_prefs.pb
    weather_prefs.pb
```

API keys are intentionally excluded. Manifest records their absence so restore can prompt the user.

**Export flow:**
1. `CHECKPOINT` WAL on the DB (flush to main file), then copy `closet.db`
2. Copy image files
3. Copy DataStore `.pb` files
4. ZIP everything → write via SAF `ACTION_CREATE_DOCUMENT` (saves to user-chosen location)

**Import flow:**
1. User picks `.hangr` file via SAF `ACTION_OPEN_DOCUMENT`
2. Read manifest — validate schema version compatibility
3. Stop DB connection, replace `closet.db`, reopen
4. Copy images into `closet_images/`, overwrite on conflict
5. Replace `.pb` pref files
6. If manifest schema version < current app version → run Room migrations on restored DB
7. Show banner: "API keys were not restored — re-enter in Settings"

**UI:** Two buttons in Settings → "Export backup" / "Restore from backup". Progress indicator for large image sets.

---

## Phase 2 — Android Auto Backup (zero-effort cloud)

`res/xml/backup_rules.xml` — include `closet.db`, `closet_images/`, the three `.pb` files; exclude `ai_keys_encrypted` (device-locked, would corrupt on a different device).

Runs automatically. No UI needed. Complements Phase 1 — Auto Backup has a 25 MB cap on older Android so large wardrobes will overflow; Phase 1 remains the full backup path.

---

## Phase 3 — Scheduled auto-export (deferred)

Periodic WorkManager job that writes a `.hangr` to a user-chosen folder (persisted SAF URI). Rolling local backup without user intervention.

---

## Decisions

| # | Question | Decision |
|---|----------|----------|
| 1 | Conflict strategy on restore | **Overwrite** — restore implies "replace this install with the backup" |
| 2 | Schema version mismatch | **Run Room migrations** on the restored DB before reopening |
| 3 | Image conflict on restore | **Overwrite** — same UUID = same logical file; backup is authoritative |
| 4 | Large backup progress / cancellation | **Foreground service + notification** — must survive app backgrounding for large wardrobes |
| 5 | Restore safety | **Require confirmation dialog** — data loss is irreversible |
| 6 | In-progress ZIP location | **`filesDir/backup_temp/`** — `cacheDir` can be OS-evicted mid-write; explicit cleanup after SAF handoff |
