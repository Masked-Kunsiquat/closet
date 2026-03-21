# Wear History / OOTD Journal — Roadmap

## What's already done (data layer is complete)

- `LogDao` — queries for logs by date, calendar range, OOTD marking
- `LogRepository` — `wearOutfitToday`, `getCalendarDaysForMonth`, `setOotd`, `deleteLog`
- `OutfitLogWithMeta` — joined query with outfit name, item count, cover image
- `CalendarDay` — monthly summary with dot/OOTD indicators
- Bottom navigation bar with 3 tabs: Closet, Outfits, Journal

---

## Phase 1 — Bottom Navigation ✅

- [x] **1.1** Add `NavigationBar` to root scaffold with 3 tabs: Closet, Outfits, Journal
- [x] **1.2** Add `JournalRoute` `@Serializable` destination (`JournalNavigation.kt` in outfits module)
- [x] **1.3** Register `journalScreen()` extension in `ClosetNavGraph.kt`
- [x] **1.4** Wire bottom nav selection to `NavController` using `saveState`/`restoreState` so tab switches do not re-compose

---

## Phase 2 — Journal Screen (Calendar View) ✅

- [x] **2.1** `JournalViewModel` — `currentYearMonth`, `calendarDays`, `selectedDate` state; `flatMapLatest` switches month flow reactively; `nextMonth()` blocked at current month
- [x] **2.2** `JournalScreen` — month nav header, Mon–Sun day labels, calendar grid with day cells (dot for logged days, accent color + bold for OOTD, today highlight, selection ring)
- [x] **2.3** Empty month state — "Wear an outfit to start your journal"

---

## Phase 3 — Day Detail Bottom Sheet ✅

`LogDao.getLogsByDate()` returns `OutfitLogWithMeta` with cover image, outfit name, item count — ready to display.

- [x] **3.1** `DayDetailSheet` — `ModalBottomSheet` triggered by calendar day tap
  - Header: formatted date (e.g. "Saturday, March 21")
  - List of `OutfitLogWithMeta` cards (cover image thumbnail, outfit name, item count)
  - OOTD star toggle per log entry — tap to crown, tap again to clear (calls `logRepository.setOotd` / `clearOotd`)
  - Delete button per log entry (calls `logRepository.deleteLog(logId)`)
- [x] **3.2** Day detail state lives in `JournalViewModel` (not a separate VM)
  - `logsForSelectedDate` derived via `flatMapLatest` on `_selectedDate`
  - `toggleOotd(logId, currentIsOotd)`, `deleteLog(logId)` actions added
  - `resolveImagePath(path)` helper added (delegates to `StorageRepository`)

---

## Phase 4 — Log a Past Outfit (Retroactive Logging) ✅

Currently "Wear Today" only works for today. Users need to log outfits for past dates.

- [x] **4.1** `LogRepository.wearOutfitOnDate(outfitId, date)` — added; `wearOutfitToday` now delegates to it
- [x] **4.2** `OutfitPickerForDate` sheet — lists all saved outfits with `OutfitPreview` thumbnail; tap to log on the selected date
- [x] **4.3** "Log outfit" button in `DayDetailSheet` header opens `OutfitPickerForDate`; selecting an outfit calls `wearOutfitOnDate` and closes the picker; back arrow / dismiss returns to day detail sheet

---

## Phase 5 — Log Entry Edit (Notes + Weather) ✅

The schema supports `notes`, `temperature_low`, `temperature_high`, `weather_condition` but the current write path only sets `outfitId` and `date`.

- [x] **5.1** `LogEditSheet` — sheet for editing an existing log entry
  - Notes `OutlinedTextField` (multi-line, blank saves as null)
  - Weather condition `FilterChip` selector with icons (Sunny/PartlyCloudy/Cloudy/Rainy/Snowy/Windy) using `FlowRow`; tap selected chip to deselect
  - Low/high temp fields deferred
  - Save constructs `OutfitLogEntity` from `OutfitLogWithMeta` (preserves outfitId, date, isOotd, createdAt) and calls `logRepository.updateLog(...)`
- [x] **5.2** Tapping any `LogCard` in `DayDetailSheet` opens the edit sheet; notes/weather summary shown on card when set

---

## Phase 6 — Item Wear History (Item Detail integration) ✅

Close the loop from the item's perspective — see when and in what outfits an item was worn.

- [x] **6.1** `LogDao.getLogsForItem(clothingItemId)` — joins `outfit_logs → outfit_items` (filtered by item) → `outfits`; returns `ItemWearLog` (id, date, isOotd, outfitName) ordered DESC
- [x] **6.2** `LogRepository.getLogsForItem(itemId)` wrapping the DAO query
- [x] **6.3** `WearHistorySection` added to `ClothingDetailScreen`
  - Full chronological list (most-recent first), OOTD star indicator per row
  - "Never worn yet" empty state
  - Tapping a row navigates to Journal via `JournalRoute(initialDate = date)`
  - `JournalRoute` changed from `object` → `data class JournalRoute(val initialDate: String? = null)`
  - `JournalViewModel.navigateToDate(date)` jumps calendar to the correct month and opens the day detail sheet immediately

---

## Build order

```
Phase 1 (bottom nav)
  → Phase 2 (calendar)
    → Phase 3 (day detail)
      → Phase 5 (log edit)
      → Phase 4 (retroactive log)
Phase 6 (item wear history) — independent, pick up any time after Phase 3
```
