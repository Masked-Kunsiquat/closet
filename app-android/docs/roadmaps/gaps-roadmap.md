# UI/UX Gaps — Roadmap

Tracks gaps between what the data layer supports and what the UI actually exposes.
Derived from the March 2026 full-codebase audit.

**Severity key:**
- 🔴 **MAJOR** — feature exists in the backend but is broken or effectively unreachable in the UI
- 🟡 **MINOR** — data is stored but not surfaced; polish and completeness issues

---

## Phase 1 — Item status management ✅

`clothing_items.status` has four values (`Active | Sold | Donated | Lost`) but the status badge
on the detail screen is display-only. `ClothingFormViewModel.save()` hardcodes `Active` on new
items. Users cannot retire, sell, or archive anything.

- [x] **§1.1 — Status picker in the clothing form**
  File: `features/wardrobe/src/main/kotlin/.../ClothingFormScreen.kt` + `ClothingFormViewModel.kt`
  - Add `status: ClothingStatus` to `ClothingFormState` (default `Active`).
  - Load existing item's status in the edit path (`ClothingFormViewModel.loadItem()`).
  - Write status in `save()` instead of hardcoding `Active`.
  - UI: `ExposedDropdownMenuBox` (or a `SingleChoiceSegmentedButtonRow`) for the 4 values.
    Place near the bottom of the form alongside wash status.

- [x] **§1.2 — Status picker on the detail screen**
  File: `features/wardrobe/src/main/kotlin/.../ClothingDetailScreen.kt` + `ClothingDetailViewModel.kt`
  - Replace the read-only status `Badge` with a tappable chip.
  - Tapping opens a `ModalBottomSheet` or `AlertDialog` with the 4 status choices.
  - Add `fun updateStatus(status: ClothingStatus)` to the ViewModel — calls
    `ClothingRepository.updateStatus(itemId, status)`.
  - Add `updateStatus(id, status)` to `ClothingRepository` (single-column update, same pattern
    as `updateWashStatus`).

---

## Phase 2 — Seasons, occasions, materials, patterns in the add/edit form ✅

Multi-select attributes (colors, seasons, occasions, materials, patterns) can only be set from
the detail screen after an item is saved. Items created fresh have zero seasons and occasions,
which means they are **invisible to the recommendation engine's hard filter**. This is the
highest-priority fix.

- [x] **§2.1 — Add `seasons`, `occasions`, `materials`, `patterns` to `ClothingFormState`**
  File: `features/wardrobe/src/main/kotlin/.../ClothingFormViewModel.kt`
  - Extend `ClothingFormState` with:
    ```kotlin
    val selectedSeasonIds: Set<Long> = emptySet(),
    val selectedOccasionIds: Set<Long> = emptySet(),
    val selectedMaterialIds: Set<Long> = emptySet(),
    val selectedPatternIds: Set<Long> = emptySet(),
    ```
  - Populate from the existing item in the edit path (join IDs from junction tables).
  - Write all four junction tables in `save()` using the existing `set*` helpers in
    `ClothingRepository` (already used for colors).

- [x] **§2.2 — Expose lookup lists for the pickers**
  `LookupDao` already has `getAllSeasons()`, `getAllOccasions()`, `getAllMaterials()`,
  `getAllPatterns()`. Load them in `ClothingFormViewModel.init {}` alongside the existing
  category/brand lists and expose as `StateFlow`.

- [x] **§2.3 — Add pickers to `ClothingFormScreen`**
  Reuse the `MultiSelectChipRow` pattern already used for colors. Add four rows:
  - Seasons (icons from `SeasonEntity.icon`)
  - Occasions (icons from `OccasionEntity.icon`)
  - Materials (text-only chips)
  - Patterns (text-only chips)
  Place them after the colors row in the form scroll column.

---

## Phase 3 — "Log it" from recommendations 🔴

`onNavigateToLog = null` in `ClosetNavGraph.kt` line ~213 disables the "Log it" button on
recommendation result cards. The blocker is that `OutfitBuilderDestination` has no
`preselectedItemIds` param.

- [ ] **§3.1 — Add `preselectedItemIds` to `OutfitBuilderDestination`**
  File: `features/wardrobe/src/main/kotlin/.../WardrobeNavigation.kt`
  ```kotlin
  @Serializable
  data class OutfitBuilderDestination(
      val outfitId: Long? = null,
      val preselectedItemIds: List<Long> = emptyList(),
  )
  ```

- [ ] **§3.2 — Consume `preselectedItemIds` in `OutfitBuilderViewModel`**
  File: `features/outfits/src/main/kotlin/.../OutfitBuilderViewModel.kt`
  - In `init {}`, if `destination.preselectedItemIds` is non-empty, set
    `_selectedItems` from those IDs (load `ClothingItemWithMeta` via repository).

- [ ] **§3.3 — Wire `onNavigateToLog` in `ClosetNavGraph.kt`**
  Replace `onNavigateToLog = null` with:
  ```kotlin
  onNavigateToLog = { itemIds ->
      navController.navigate(OutfitBuilderDestination(preselectedItemIds = itemIds))
  }
  ```

---

## Phase 4 — Bulk Wash discoverability 🟡

`BulkWashScreen` is registered in the nav graph and reachable via the "Laundry Day" launcher
shortcut, but there is no in-app navigation path to it. Users who don't use the shortcut
will never find this feature.

- [ ] **§4.1 — Add a Bulk Wash entry point in Settings**
  File: `features/settings/src/main/kotlin/.../SettingsScreen.kt`
  - Add a `SettingsItem` row (e.g. "Laundry Day — bulk mark wash status") in the Wardrobe
    section of the settings list.
  - Pass `onNavigateToBulkWash: () -> Unit` lambda from `ClosetNavGraph` through the
    settings screen to the row.

---

## Phase 5 — Closet screen sorting 🟡

Items are always ordered `created_at DESC` (hardcoded in `ClothingDao`). No UI exists to
change the sort order.

- [ ] **§5.1 — Add a `SortOrder` enum**
  File: `features/wardrobe/src/main/kotlin/.../ClosetViewModel.kt`
  ```kotlin
  enum class SortOrder { NEWEST, OLDEST, NAME_AZ, NAME_ZA, MOST_WORN, LEAST_WORN }
  ```

- [ ] **§5.2 — Sort in-memory in `ClosetViewModel`**
  The existing `filteredAndSorted` flow already does in-memory filtering. Apply a
  `sortedWith(comparator)` step after filtering based on `_sortOrder: StateFlow<SortOrder>`.
  `MOST_WORN` / `LEAST_WORN` use the `wearCount` field already present on
  `ClothingItemWithMeta`.

- [ ] **§5.3 — Add a sort button to `ClosetScreen`**
  Place a sort `IconButton` (SortAscending/SortDescending icon) in the top app bar or
  alongside the filter chip row. Tapping opens a `ModalBottomSheet` or `DropdownMenu`
  with the sort options.

---

## Phase 6 — Status filter on the Closet screen ✅

Depends on Phase 1 (status must be settable before filtering by it is useful). The closet
currently shows all items regardless of status; once users can mark items as Sold/Donated/Lost
they'll need a way to hide them.

- [x] **§6.1 — Default to Active-only in `ClosetViewModel`**
  Added in-memory filter in the `combine` block: `.filter { fs.showArchived || it.item.status == ClothingStatus.Active }`.
  Consistent with the existing in-memory filtering pattern; no DAO change needed.

- [x] **§6.2 — Add a status filter chip to `ClosetScreen`**
  "Show archived" `FilterChip` appended to `CategoryFilterRow`. Backed by `_showArchived: MutableStateFlow<Boolean>(false)`
  in `ClosetViewModel`; does not count toward the filter badge.

---

## Phase 7 — Show purchase date and location on the detail screen 🟡

`purchase_date` and `purchase_location` are stored in the DB and writable via the form,
but they are not displayed anywhere in the detail screen.

- [ ] **§7.1 — Add purchase metadata rows to `ClothingDetailScreen`**
  File: `features/wardrobe/src/main/kotlin/.../ClothingDetailComponents.kt`
  - Below the existing price / cost-per-wear `DetailStatRow` entries, add:
    - "Purchased" — formatted `purchase_date` (e.g. "March 2024"), null-guarded
    - "From" — `purchase_location` text, null-guarded
  - `ClothingDetailViewModel` already loads the full `ClothingItemDetail` entity; both
    fields are already in scope.

---

## Phase 8 — Single-item wear logging (no outfit required) 🔴

The entire wear tracking system requires an outfit. Logging a single item as worn means
creating a one-item outfit, which is friction-heavy. This is the most architecturally
significant gap.

> **Note:** This phase introduces a new log type. Approach options:
> - **Option A (lightweight):** Allow `outfit_logs.outfit_id` to be nullable, treat a null
>   outfit_id as an ad-hoc log. Snapshot items via `outfit_log_items` as usual.
> - **Option B (new table):** Add a separate `item_wear_logs` table (simpler schema,
>   cleaner queries, no null FK).
> Option A is recommended — it reuses all existing stat queries with minimal schema change.

- [ ] **§8.1 — Make `outfit_logs.outfit_id` nullable (migration)**
  File: `core/data/src/main/kotlin/.../ClothingDatabase.kt`
  - Write `Migration(N, N+1)` that drops and recreates `outfit_logs` without `NOT NULL` on
    `outfit_id`.
  - Update `OutfitLogEntity.outfitId` to `val outfitId: Long?`.
  - Follow the migration checklist in `migrations/AGENTS.md` (drop `one_ootd_per_day` index
    at top of migration, recreate in `onOpen`).

- [ ] **§8.2 — Add `LogRepository.wearItemsToday(itemIds: List<Long>)`**
  Creates an `OutfitLogEntity` with `outfitId = null`, inserts snapshot rows into
  `outfit_log_items` for each item ID. Reuses `LogRepository.insertLogAndSnapshot()`.

- [ ] **§8.3 — "Log wear" action on `ClothingDetailScreen`**
  Add a "Log wear" button (or secondary FAB) to the detail screen. Tapping calls
  `ClothingDetailViewModel.logWearToday()` which calls `wearItemsToday(listOf(itemId))`.
  Show a snackbar confirmation on success.

- [ ] **§8.4 — Ensure null-outfit logs are excluded from outfit-specific queries**
  Audit `OutfitDao` and `StatsDao` queries that join `outfit_logs → outfits` — add
  `WHERE outfit_id IS NOT NULL` guards where appropriate so null-outfit logs don't surface
  as broken outfit entries.

---

## Build order

```
Phase 2 (attribute form pickers)        ← highest impact, unblocks recommendations
Phase 1 (item status)
  → Phase 6 (status filter)             ← blocked on Phase 1 being useful
Phase 3 (Log it from recs)              ← small lift, big UX payoff
Phase 4 (Bulk Wash discoverability)     ← one row in Settings
Phase 5 (Closet sorting)               ← self-contained
Phase 7 (purchase metadata on detail)  ← purely additive
Phase 8 (single-item wear logging)     ← most complex, requires migration
```

---

## Files summary

| Status | File | Change |
|--------|------|--------|
| [ ] | `features/wardrobe/.../ClothingFormViewModel.kt` | Phase 1.1: status in form state; Phase 2.1-2.2: junction attributes |
| [ ] | `features/wardrobe/.../ClothingFormScreen.kt` | Phase 1.1: status picker; Phase 2.3: season/occasion/material/pattern pickers |
| [ ] | `features/wardrobe/.../ClothingDetailScreen.kt` | Phase 1.2: tappable status; Phase 7.1: purchase date/location rows |
| [ ] | `features/wardrobe/.../ClothingDetailViewModel.kt` | Phase 1.2: `updateStatus()` |
| [ ] | `core/data/.../repository/ClothingRepository.kt` | Phase 1.2: `updateStatus()` single-column update |
| [ ] | `features/wardrobe/.../WardrobeNavigation.kt` | Phase 3.1: `preselectedItemIds` on `OutfitBuilderDestination` |
| [ ] | `features/outfits/.../OutfitBuilderViewModel.kt` | Phase 3.2: consume `preselectedItemIds` |
| [ ] | `app/.../navigation/ClosetNavGraph.kt` | Phase 3.3: wire `onNavigateToLog` |
| [ ] | `features/settings/.../SettingsScreen.kt` | Phase 4.1: Bulk Wash entry point |
| [ ] | `features/wardrobe/.../ClosetViewModel.kt` | Phase 5.1-5.2: `SortOrder` enum + in-memory sort |
| [ ] | `features/wardrobe/.../ClosetScreen.kt` | Phase 5.3: sort button UI |
| [ ] | `core/data/.../dao/ClothingDao.kt` | Phase 6.1: status filter param |
| [ ] | `core/data/.../ClothingDatabase.kt` | Phase 8.1: migration for nullable `outfit_id` |
| [ ] | `core/data/.../entity/OutfitLogEntity.kt` | Phase 8.1: `outfitId: Long?` |
| [ ] | `core/data/.../repository/LogRepository.kt` | Phase 8.2: `wearItemsToday()` |
