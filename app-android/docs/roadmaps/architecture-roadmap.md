# Architecture Roadmap

Findings from codebase audit. Items ordered by priority.

---

## Phase 1 — God Files (Immediate)

### `ClothingFormScreen.kt` (916 lines)
- [x] Extract `CategoryDropdown` and `SubcategoryDropdown` into a generic `DropdownSelector<T>` composable — they are nearly identical
- [x] Extract `ColorSelectionGrid`, `DatePickerField`, `BrandAutocompleteField`, and other private composables into `FormComponents.kt`
- [x] Move all `@Preview` functions and mock data into `FormPreviews.kt`
- [x] Ensure `ClothingFormScreen.kt` contains only the top-level screen composable and `ClothingFormTopBar`/`ClothingFormContent`

### `ClothingDetailScreen.kt` (731 lines)
- [x] Replace the 5 copy-pasted `MultiSelectSheet` blocks (seasons, occasions, colors, materials, patterns) with a data-driven loop or single generic helper composable
- [x] Extract `ClothingAttributes` and `AttributeSection`/`AttributeChip` into `ClothingDetailComponents.kt`
- [x] Move `@Preview` functions into `ClothingDetailPreviews.kt`

### `BrandManagementScreen.kt` (405 lines)
- [x] Extract dialog/edit state into a sealed class (`BrandDialogState`) instead of separate boolean flags
- [x] Extract dialog composables (`AddBrandRow`, `EditBrandRow`) into `BrandComponents.kt`

---

## Phase 2 — ViewModel State (Short-term)

### `ClothingFormViewModel.kt` (468 lines, 13 `MutableStateFlow`s)
- [x] Consolidate 13 individual `MutableStateFlow` field properties into a single `ClothingFormUiState` data class exposed as one `StateFlow` — update via `copy()`
- [x] Extract the `isDirty` computation (currently ~20 lines of field comparisons) into a standalone function
- [x] Audit `loadItemForEditing` — it uses `.first()` blocking calls; replace with structured `collectLatest` or proper error handling

### `ClothingDetailViewModel.kt`
- [x] Combine the 6 separate `StateFlow`s (categories, seasons, occasions, etc.) into a single `UiState` `StateFlow` to eliminate synchronization risk

---

## Phase 3 — Shared Utilities (Short-term)

- [x] Create a shared `ErrorSnackbar` / error-handling composable in `core/ui/components/` — currently duplicated across 4 screens
- [x] Extract `wear_count` SQL subquery in `ClothingDao.kt` to a named Kotlin constant — currently copy-pasted in `getAllClothingItems` and `getClothingItemById`
- [x] Confirm `resolveImagePath` usage is consistent across `ClosetScreen`, `ClothingDetailScreen`, `WardrobePickerScreen` — extract to a shared util in `core/ui/` if not already

---

## Phase 4 — Database & Migrations (Medium-term)

- [x] Move inline `MIGRATION_1_2`, `MIGRATION_3_4` objects out of `ClothingDatabase.kt` into a `migrations/` package (e.g. `Migration1To2.kt`) — standard multi-module convention
- [x] Extract column-existence raw query in migrations into a reusable helper (e.g. `columnExists(db, table, column): Boolean`)
- [x] Evaluate whether `DatabaseSeeder` hard-coded seed entries should be loaded from a bundled JSON/CSV resource file to reduce maintenance burden on code changes — **decision: keep in Kotlin.** Data is small (~150 rows) and stable; JSON/CSV would require threading `Context` into `SupportSQLiteDatabase` callbacks, add a parser + error handling path, and lose compile-time safety for no meaningful gain.

---

## Architectural Strengths — Do Not Change

- Module dependency graph: `app → features → core/ui → core/data` — no violations, keep it this way
- `DataResult<T>` + `AppError` sealed class hierarchy — well-designed, use as the template
- `ClosetScreen.kt` — use as the reference implementation for all screen structure
- Hilt `@Singleton` scoping in `DataModule` — correct, do not scatter providers
- Room migrations with backfill logic — correct pattern, just needs cleanup
- Relative image path storage + reconstruction at display time — matches spec, do not change
