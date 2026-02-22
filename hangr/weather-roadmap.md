# Wear History & Outfit Association Plan

## Context
Two screens need new sections:
1. **Outfit detail** (`outfit/[id].tsx`) — below the item grid, show a "Wear History" list of every time this outfit was logged, with inline editing so past log entries (dates, weather, notes) can be corrected without navigating away.
2. **Item detail** (`item/[id].tsx`) — two new sections at the bottom of the ScrollView:
   - "Appears In" — horizontal card strip of outfits that contain this item
   - "Wear History" — compact rows of every log entry for any outfit that includes this item

## Files to Change

| # | File | What changes |
|---|------|-------------|
| 1 | `db/queries.ts` | Add `updateOutfitLog`, `getLogsForOutfit`, `getOutfitsForItem`, `getLogsForItem` |
| 2 | `hooks/useOutfitLog.ts` | Add `useLogsForOutfit` hook |
| 3 | `app/outfit/[id].tsx` | Add Wear History section below item grid; edit log inline via bottom-sheet modal |
| 4 | `app/item/[id].tsx` | Add "Appears In" + "Wear History" sections |

---

## Step 1 — New queries: `db/queries.ts`

### 1a. `updateOutfitLog`
Updates date, notes, and weather fields. `is_ootd` is intentionally excluded — OOTD toggling uses the existing `setOotd`/`clearOotd` helpers to maintain the partial unique index invariant.

```typescript
export async function updateOutfitLog(
  db: SQLiteDatabase,
  logId: number,
  log: {
    date: string;
    notes: string | null;
    temperature_low: number | null;
    temperature_high: number | null;
    weather_condition: WeatherCondition | null;
  }
): Promise<void>
```
SQL: `UPDATE outfit_logs SET date=?, notes=?, temperature_low=?, temperature_high=?, weather_condition=? WHERE id=?`

### 1b. `getLogsForOutfit`
All logs for a specific outfit, ordered by date DESC.
```typescript
export async function getLogsForOutfit(
  db: SQLiteDatabase,
  outfitId: number
): Promise<OutfitLog[]>
```
SQL: `SELECT * FROM outfit_logs WHERE outfit_id = ? ORDER BY date DESC`

### 1c. `getOutfitsForItem`
All outfits that contain a clothing item, with item_count and cover_image metadata.
```typescript
export async function getOutfitsForItem(
  db: SQLiteDatabase,
  itemId: number
): Promise<OutfitWithMeta[]>
```
SQL mirrors `getAllOutfits` with an added `WHERE oi.clothing_item_id = ?` join filter.

### 1d. `getLogsForItem`
All outfit_logs for any outfit containing the item, returning `OutfitLogWithMeta[]` ordered by date DESC.
```typescript
export async function getLogsForItem(
  db: SQLiteDatabase,
  itemId: number
): Promise<OutfitLogWithMeta[]>
```
SQL: JOIN outfit_logs → outfit_items filtered by clothing_item_id, LEFT JOIN outfits for outfit_name, GROUP BY ol.id for item_count and cover_image subqueries.

---

## Step 2 — New hook: `hooks/useOutfitLog.ts`

Add `useLogsForOutfit(outfitId: number)` following the exact same pattern as `useLogsForDate`:
- State: `{ logs: OutfitLog[], loading: boolean, error: string | null }`
- Calls `getLogsForOutfit(db, outfitId)` on mount / when `outfitId` changes
- Returns `{ logs, loading, error, refresh }`

---

## Step 3 — Outfit detail: `app/outfit/[id].tsx`

### Structure
Keep the existing `FlatList` for outfit items. Add a `ListFooterComponent` that renders the Wear History section beneath — avoids nested scrolling issues.

### Wear History section (as `ListFooterComponent`)
Component `WearHistorySection` owns `useLogsForOutfit(outfitId)` internally.

Each log row shows:
- Formatted date (e.g., "Feb 21, 2026")
- OOTD badge if `is_ootd === 1`
- Weather emoji + condition + temp range if any weather data present
- Notes snippet (numberOfLines=1)

Tapping a row opens `EditLogModal` pre-filled with that log's data.

### `EditLogModal` component (same file)
Bottom-sheet modal, same layout as `LogModal`. Differences:
- Receives `log: OutfitLog` to pre-fill all fields
- `is_ootd` shown as toggleable (uses `setOotd`/`clearOotd`)
- On save: calls `updateOutfitLog` (for non-ootd fields) + handles OOTD toggle
- Includes a "Delete Log" button (calls `deleteOutfitLog`)
- On save or delete: calls `onSaved()` callback → triggers `refresh()` in `WearHistorySection`
- No navigation on save — just closes

---

## Step 4 — Item detail: `app/item/[id].tsx`

### Data loading
After existing item load, fire a parallel fetch:
```typescript
Promise.all([getOutfitsForItem(db, itemId), getLogsForItem(db, itemId)])
```
Store results in local state (`outfits`, `itemLogs`).

### "Appears In" section
- Rendered using the existing `Section` component with title "Appears In"
- Horizontal `ScrollView` of outfit cards (80×100 cover image or emoji + outfit name below)
- Tappable → `router.push('/outfit/[id]', { id: outfit.id })`
- Omit entire section if `outfits.length === 0`

### "Wear History" section
- Rendered using the existing `Section` component with title "Wear History"
- Each `OutfitLogWithMeta` row: date, OOTD star, outfit name, weather emoji + temp range, notes snippet
- Read-only (no tap-to-edit from here — editing belongs to outfit detail / log/[date])
- Omit entire section if `itemLogs.length === 0`

### Placement
Details → Notes → **Appears In** → **Wear History** → Delete Item button

---

## Verification
1. Log an outfit → open outfit detail → wear history section shows below item grid
2. Tap a log row → edit modal opens pre-filled; change temp → save → row updates
3. Delete a log from edit modal → row disappears
4. Open item detail for an item in that outfit → "Appears In" shows the outfit card; tap → navigates to outfit detail
5. "Wear History" on item detail shows the log with date + weather
6. Item with no outfits/logs → both sections hidden
