# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**hangr** — a personal, local-first digital wardrobe and outfit tracking app. React Native / Expo. SQLite on-device. No accounts, no cloud, no telemetry.

All app code lives in `hangr/`. Run all commands from that directory.

## Commands

```bash
cd hangr

npx expo start          # Start dev server (choose iOS/Android/web interactively)
npx expo start --ios    # iOS simulator
npx expo start --android
npx expo lint           # ESLint via expo lint (eslint-config-expo/flat)
```

**Installing packages — always use expo install, never npm install directly:**
```bash
npx expo install <package-name>
```

There are no tests yet.

## Architecture

### Routing
expo-router with file-based routing. All screens live in `hangr/app/`:
- `(tabs)/index.tsx` — Closet screen (main screen, grid/list)
- `item/add.tsx` — Add Item
- `item/[id].tsx` — Item Detail
- `item/[id]/edit.tsx` — Edit Item
- `_layout.tsx` — Root layout: initializes DB, wraps with `AccentProvider` + `ThemeProvider`

### Database
`expo-sqlite`, opened as a singleton in `db/index.ts` via `getDatabase()` (returns a `Promise<Db>`). Always `await getDatabase()` before any query.

Initialization order on first call: `PRAGMA foreign_keys = ON` → `runMigrations()` → `runSeeds()`.

**Migrations** live in `db/migrations/`. Each file exports `{ version: number, up(db): Promise<void> }`. The runner tracks applied versions in the `schema_migrations` table. Never edit an applied migration — add a new one.

**Seeds** live in `db/seeds/`. All inserts use `INSERT OR IGNORE` (idempotent). Seeds run after every cold start but only insert missing rows.

**Queries** live in `db/queries.ts`. Never store derived fields — `wear_count` and `cost_per_wear` are always computed at query time.

**Types** live in `db/types.ts`. These mirror the schema exactly.

### Design system
All tokens in `constants/tokens.ts`. Never hardcode colors, spacing, font sizes, or radii — always import from tokens.

- `Palette` — dark-first surface layers (`surface0`–`surface3`), text, border, semantic colors
- `Spacing` — 4pt grid
- `Radius`, `FontSize`, `FontWeight`, `FontFamily`, `Shadow`, `Duration`
- `AccentPalettes` — 6 named accent themes (amber/coral/sage/sky/lavender/rose), each with `primary`, `muted`, `subtle`

`constants/theme.ts` is a legacy shim — new code should import from `constants/tokens.ts` directly.

### Accent color
`context/AccentContext.tsx` provides `useAccent()` → `{ accentKey, accent, setAccent }`. The active `accent` object has `.primary`, `.muted`, `.subtle`. Default accent is `amber`. Use `accent.primary` for CTAs, active states, selection rings.

### Icon system
Phosphor SVGs are bundled locally in `assets/icons/`. Use `<PhosphorIcon name="t-shirt" size={24} color={...} />` — the component maps name strings to the 28 local assets via `expo-image`. Icon names stored in DB (on `categories`, `seasons`, `occasions`) match the filename without `.svg`.

### Key hooks
- `useClothingItems()` — full list with category/subcategory name + `wear_count` joined
- `useClothingItem(id)` — single item + all junction IDs + `wearCount` + `costPerWear` (null-safe)
- `useClosetView(items)` — view mode (grid/list), sort, filters, memoized `filteredAndSorted`

### Components
- `components/clothing/ItemForm.tsx` — shared Add/Edit form. All fields. `EMPTY_FORM` constant for new items.
- `components/closet/FilterPanel.tsx` — bottom-sheet modal. Resolves color/season/occasion junction filters to a `Set<number>` of item IDs before committing atomically.
- `components/PhosphorIcon.tsx` — SVG icon renderer

## Key conventions

- **Image paths are always relative** — never absolute, never `file://`. Store the URI returned by expo-image-picker as-is.
- **Junction tables** follow the pattern `clothing_item_colors`, `clothing_item_materials`, etc. Use the `set*` helpers in `queries.ts` (delete-then-insert, not append).
- **Wear count** = `COUNT(DISTINCT outfit_logs.id)` joined through `outfit_items`. Never a column.
- **Cost per wear** = `purchase_price / wear_count`. Return `null` when wears = 0 or price is null.
- **OOTD constraint** is enforced at DB level via `CREATE UNIQUE INDEX one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1`.
- `clothing_items.status`: `'Active' | 'Sold' | 'Donated' | 'Lost'` — default `'Active'`
- `clothing_items.wash_status`: `'Clean' | 'Dirty'`
- `waist` and `inseam` are `REAL` to support half sizes (e.g. `32.5`)

## What is deferred — do not build
Collage builder, outfit planning for future dates, packing lists, weather API, goals/missions, cloud backup, color dropper from image.
