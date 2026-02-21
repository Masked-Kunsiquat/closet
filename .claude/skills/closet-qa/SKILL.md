---
name: closet-qa
description: QA and edge case review for the Closet app. Use when testing features, reviewing code for edge cases, checking error handling, or thinking through what could go wrong in the Closet app specifically.
allowed-tools: Read, Grep, Glob
---

# Closet — QA & Edge Cases

## Your Job
Think about what breaks. Not just happy path — the weird states, empty data, first launch, corrupted input, missing images. This is a local-first SQLite app — data integrity and offline resilience matter a lot.

## First Launch / Empty State
- [ ] No clothing items — does the Closet screen show a useful empty state?
- [ ] No outfits logged — does the Journal/Calendar show something meaningful?
- [ ] No brands, no locations seeded by user — do dropdowns handle empty gracefully?
- [ ] First-time DB setup: do migrations and seeds run cleanly in order?
- [ ] Does `PRAGMA foreign_keys = ON` get set before any queries run?

## Clothing Items
- [ ] Item with no image — does it show a placeholder everywhere it appears?
- [ ] Item with 0 wears — does Cost Per Wear handle division by zero? (Return null/dash, not crash)
- [ ] Item with status Sold/Donated/Lost — does it still appear correctly in wear history?
- [ ] Item deleted — are all junction table rows cleaned up? (ON DELETE CASCADE)
- [ ] Very long item name — does it truncate gracefully in cards and lists?
- [ ] Item with no brand, no category, no size — are all fields truly optional where intended?

## Outfit Logging
- [ ] Two outfit logs on the same day — both allowed, calendar shows count correctly
- [ ] Two logs on same day both trying to be OOTD — second one must be rejected (DB constraint)
- [ ] Outfit log deleted — do wear counts update correctly everywhere?
- [ ] Outfit with 0 items — should this be allowed? (Probably not — validate)
- [ ] Outfit logged on today's date vs. a past date — both should work

## Images
- [ ] Image file missing from filesystem but path exists in DB — graceful placeholder, no crash
- [ ] Image path stored as absolute — this is a bug, flag it
- [ ] Large image — is it being resized before storage or stored raw?
- [ ] No image taken — item still saves cleanly

## Stats
- [ ] All stats with zero data — no division by zero, no empty chart crashes
- [ ] Time range filter with no data in range — meaningful empty state
- [ ] Item worn 0 times appears in "Never Worn" list
- [ ] Cost per wear with 0 wears displays as `—` not `∞` or crash

## Sizing
- [ ] Waist/inseam as REAL — does 32.5 save and display correctly?
- [ ] Item with no size set — handled gracefully in detail view

## Settings & Localization
- [ ] Currency symbol change — does it update everywhere prices display?
- [ ] Temperature unit change — relevant once weather is added, but tokens should exist
- [ ] Week start day change — does calendar respect it?

## Database
- [ ] Migrations run in order on fresh install
- [ ] Migrations are idempotent if re-run (they shouldn't re-run, but verify the runner guards this)
- [ ] Seeds don't duplicate rows on re-run (`INSERT OR IGNORE`)
- [ ] Deleting a category — what happens to items in that category? (Define and enforce)

## General
- [ ] App killed mid-write — does SQLite transaction leave DB in a valid state?
- [ ] Very large closet (500+ items) — does the gallery still perform?
- [ ] Filter + sort combination — does it work correctly together, not just independently?