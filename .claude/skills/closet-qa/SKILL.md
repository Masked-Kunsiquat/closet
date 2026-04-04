---
name: closet-qa
description: QA and edge case review for the Closet app. Use when testing features, reviewing code for edge cases, checking error handling, or thinking through what could go wrong in the Closet app specifically.
allowed-tools: Read, Grep, Glob
---

# Closet — QA & Edge Cases

## Your Job
Think about what breaks. Not just happy path — the weird states, empty data, first launch, corrupted input, missing images. This is a local-first Room/SQLite app — data integrity and offline resilience matter a lot.

## First Launch / Empty State
- [ ] No clothing items — does the Closet screen show a useful empty state?
- [ ] No outfits logged — does the Journal/Calendar show something meaningful?
- [ ] First-time DB setup: do migrations and seeds run cleanly via `DatabaseSeeder`?
- [ ] Does `PRAGMA foreign_keys = ON` get set before any queries run?
- [ ] Embedding index not yet built — does Chat show the "index building" notice?

## Clothing Items
- [ ] Item with no image — does it show a placeholder everywhere it appears?
- [ ] Item with 0 wears — does Cost Per Wear return null/dash, not crash or divide-by-zero?
- [ ] Item with status Sold/Donated/Lost — does it still appear correctly in wear history?
- [ ] Item deleted — are all junction table rows cleaned up? (`ON DELETE CASCADE`)
- [ ] Very long item name — does it truncate gracefully in cards and lists?
- [ ] Item with no brand, no category, no size — are all fields truly optional where intended?

## Outfit Logging
- [ ] Two outfit logs on the same day — both allowed, calendar shows count correctly
- [ ] Two logs on same day both trying to be OOTD — second must be rejected (partial index constraint)
- [ ] Outfit log deleted — do wear counts update correctly everywhere?
- [ ] Outfit with 0 items — validate and reject before save

## Images
- [ ] Image file missing from filesystem but path exists in DB — graceful placeholder, no crash
- [ ] Absolute path stored in DB — this is a bug, flag it
- [ ] Large image incoming — is it being compressed via `StorageRepository.saveImage()`?
- [ ] Gallery URI — is it being read in a single pass (open once, copy, close)?
- [ ] Segmented image on API < 30 — falls back to PNG, not WebP
- [ ] No image taken — item still saves cleanly

## Stats
- [ ] All stats with zero data — no division by zero, no empty chart crashes
- [ ] Time range filter with no data in range — meaningful empty state, no Vico crash
- [ ] Item worn 0 times appears in "Never Worn" list
- [ ] Cost per wear with 0 wears displays as `—` not `∞` or crash

## Background Workers
- [ ] `ImageCompressionWorker` — does it skip files that already meet both thresholds?
- [ ] `ImageCompressionWorker` — does `done` only increment when the rename actually happened?
- [ ] `EmbeddingWorker` — does it handle items with null `semantic_description` gracefully?
- [ ] Worker cancelled mid-run — is `CancellationException` propagated, not swallowed?

## Database / Migrations
- [ ] Migrations run in order on fresh install via Room's `addMigrations()`
- [ ] Every new migration drops `one_ootd_per_day` index unconditionally at the top
- [ ] Migration tests pass: `./gradlew connectedAndroidTest` before any schema PR
- [ ] Seeds don't duplicate rows on re-run (`INSERT OR IGNORE` in `DatabaseSeeder`)
- [ ] OOTD partial index created in `onOpen()`, not `onCreate()` or a migration

## Repositories & ViewModels
- [ ] All repository operations return `DataResult<T>`
- [ ] `CancellationException` is always re-thrown, never wrapped in `Result.failure`
- [ ] ViewModels use `SharingStarted.WhileSubscribed(5_000)`
- [ ] Error states map to user-friendly messages — no raw exception strings in UI

## Backup & Restore
- [ ] Backup: WAL checkpointed before copy
- [ ] Restore: canonical path traversal guard on ZIP extraction
- [ ] Restore: rejects backups from a newer schema version
- [ ] API keys excluded from backup file

## General
- [ ] App killed mid-write — does Room transaction leave DB in a valid state?
- [ ] Very large closet (500+ items) — does the gallery still perform?
- [ ] Filter + sort combination — does it work correctly together, not just independently?
- [ ] FOSS flavor — are all GMS features properly stubbed, no runtime crashes?