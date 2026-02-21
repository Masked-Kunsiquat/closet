---
name: closet-engineer
description: Core engineering conventions for the Closet app. Use when writing any code, creating files, designing architecture, adding features, or making any technical decisions for the Closet project.
---

# Closet — Engineering Conventions

## What This Project Is
A personal, local-first digital wardrobe and outfit tracking app. React Native / Expo. SQLite on-device. No accounts, no telemetry, no cloud dependency. Open source passion project.

**Mantra: built well over built fast.**

## Tech Stack
- React Native / Expo
- expo-sqlite for all data
- Local filesystem for images (relative paths ONLY — never absolute)
- Versioned migrations system (same pattern as CRM project)
- Phosphor Icons (SVG, stored as local assets; icon name stored as string in DB)

## Folder Conventions
All source lives directly under `hangr/` — there is no `src/` wrapper.
```plaintext
hangr/
  app/                    # expo-router file-based routing
    (tabs)/               # index (Closet), outfits, journal, stats, settings
    item/                 # add.tsx, [id].tsx, [id]/edit.tsx
    log/                  # [date].tsx
    outfit/               # new.tsx, [id].tsx
    _layout.tsx
  components/
    clothing/             # ItemForm.tsx, PickerSheet.tsx
    closet/               # FilterPanel.tsx
    ui/                   # SkeletonLoader.tsx, collapsible.tsx, icon-symbol.tsx
    PhosphorIcon.tsx
    haptic-tab.tsx
  context/                # AccentContext.tsx, SettingsContext.tsx
  db/
    migrations/           # 001_initial_schema.ts, 002_app_settings.ts ...
    seeds/                # categories.ts, colors.ts, seasons.ts ...
    index.ts              # getDatabase() singleton
    queries.ts            # all SQL queries
    types.ts              # TypeScript types mirroring schema
  hooks/                  # useClothingItems, useClothingItem, useClosetView, useOutfits, useStats ...
  constants/
    tokens.ts             # all design tokens (Palette, Spacing, Radius, FontSize, etc.)
    theme.ts              # legacy shim — new code imports from tokens.ts directly
  utils/
    color.ts
  assets/
    icons/                # Phosphor SVG files (32px, fill=#000000)
    images/
```

## Database Rules
- `PRAGMA foreign_keys = ON` at DB open time, always
- Never store derived fields — Wear Count and Cost Per Wear are always computed at query time
  - Wear Count = COUNT of outfit logs containing the item
  - Cost Per Wear = purchase_price / wear_count
- Image paths are always relative — never absolute, never hardcoded
- Waist and inseam stored as REAL (not a lookup) to support half sizes
- Junction tables follow pattern: `clothing_item_colors`, `clothing_item_materials`, etc.
- Only one outfit per day can be OOTD — enforced at DB level via partial unique index:
  ```sql
  CREATE UNIQUE INDEX one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1;
  ```

## Icon System
- Icon pack: Phosphor Icons (SVG files, bundled locally)
- Icon name stored as TEXT column in `categories`, `seasons`, `occasions`
- Column is nullable — UI falls back to text label gracefully
- Never store base64 or absolute paths for icons
- **SVG format**: all bundled icons must use `width="32" height="32" fill="#000000"` on the `<svg>` element — `tintColor` in expo-image only works with `fill="#000000"`, not `fill="currentColor"`

## Image Picker
- Always use `allowsEditing: true` so the user gets a crop UI
- Never set `aspect` — do not enforce any fixed crop ratio; let the user crop freely
- Use `quality: 0.85`

## Settings Persistence
- User preferences stored in `app_settings` key/value table (SQLite, migration 002)
- `getAllSettings(db)` returns `Record<string, string>`; `setSetting(db, key, value)` upserts
- `SettingsContext` (`context/SettingsContext.tsx`) exposes typed `AppSettings` and `setSetting`
- Accent key is persisted via `SettingsContext` and passed as `initialKey` to `AccentProvider` — always read accent from `AccentContext`, not directly from settings

## Clothing Item Status
`Active` | `Sold` | `Donated` | `Lost` — default `Active`. No separate selling feature in MVP.

## Wash Status
`Clean` | `Dirty`

## What Is Deferred — Do Not Build
- Collage builder
- Outfit planning (future dates)
- Packing lists / trip planning
- Weather API (manual entry only in MVP)
- Goals/missions
- Cloud backup/restore
- Color dropper from image

## General Rules
- Ask before adding a new dependency
- Prefer explicit naming over abbreviations
- Never invent fields not in the project brief
- Never store derived data in the database
- Read reference files before generating schema, seeds, or migrations