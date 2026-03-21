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

## Phase 3 — Day Detail Bottom Sheet

`LogDao.getLogsByDate()` returns `OutfitLogWithMeta` with cover image, outfit name, item count — ready to display.

- [ ] **3.1** `DayDetailSheet` — `ModalBottomSheet` triggered by calendar day tap
  - Header: formatted date (e.g. "Saturday, March 21")
  - List of `OutfitLogWithMeta` cards (cover image thumbnail, outfit name, item count)
  - OOTD star/crown toggle per log entry (calls `logRepository.setOotd(logId, date)`)
  - Delete log swipe or button (calls `logRepository.deleteLog(logId)`)
  - FAB or button to log a new outfit for that date (Phase 4)
- [ ] **3.2** `DayDetailViewModel`
  - Takes `date: String` param
  - Collects `logRepository.getLogsByDate(date)` as `StateFlow`
  - Actions: `setOotd(logId)`, `deleteLog(logId)`

---

## Phase 4 — Log a Past Outfit (Retroactive Logging)

Currently "Wear Today" only works for today. Users need to log outfits for past dates.

- [ ] **4.1** Add `LogRepository.wearOutfitOnDate(outfitId, date)` — same as `wearOutfitToday` but accepts an explicit date
- [ ] **4.2** Build `OutfitPickerForDate` sheet — lists saved outfits, tap to log on the selected date
- [ ] **4.3** Wire into `DayDetailSheet` FAB: opens outfit picker → confirms → logs and refreshes the sheet

---

## Phase 5 — Log Entry Edit (Notes + Weather)

The schema supports `notes`, `temperature_low`, `temperature_high`, `weather_condition` but the current write path only sets `outfitId` and `date`.

- [ ] **5.1** `LogEditSheet` — sheet for editing an existing log entry
  - Notes text field
  - Weather condition chip selector (Sunny / Partly Cloudy / Cloudy / Rainy / Snowy / Windy — enum already defined)
  - Optional: low/high temp fields (can defer)
  - Save calls `logRepository.updateLog(...)`
- [ ] **5.2** Surface "Edit log" action from `DayDetailSheet` entry cards

---

## Phase 6 — Item Wear History (Item Detail integration)

Close the loop from the item's perspective — see when and in what outfits an item was worn.

- [ ] **6.1** Add `LogDao` query: `getLogsForItem(clothingItemId)` — join `outfit_logs → outfit_items → clothing_items`
- [ ] **6.2** Add `LogRepository.getLogsForItem(itemId)` wrapping the above
- [ ] **6.3** Add "Wear history" section to `ClothingDetailScreen`
  - Chronological list (or count + most recent date for v1)
  - Tap entry navigates to that day in the Journal (deep link to `JournalRoute` with date param)

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
