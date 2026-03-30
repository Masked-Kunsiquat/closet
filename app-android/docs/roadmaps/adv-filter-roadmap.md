# Advanced Closet Filtering — Implementation Roadmap

Covers features 3 (Advanced Closet Filtering) and 4 (Favorites View).

**Current state:**
- `ClosetViewModel` filters by one `selectedCategoryId: Long?`, in-memory
- `ClosetScreen` has a `CategoryFilterRow` (LazyRow of FilterChips)
- `getAllItemDetails()` already eagerly loads all many-to-many relations (colors, seasons, occasions) via Room `@Relation` + `@Transaction` — **no DAO changes needed**
- `WardrobePickerViewModel` already demonstrates the multi-filter pattern to follow
- `LookupRepository` already exposes `getColors()`, `getSeasons()`, `getOccasions()`

---

## Phase 1 — ViewModel & State Foundation

**Files:** `features/wardrobe/.../ClosetViewModel.kt` only.

### 1.1 Extend `ClosetUiState`

Add filter lookup lists (to populate chips) and filter selection state:

```kotlin
data class ClosetUiState(
    // existing fields...
    val items: List<ClothingItemDetail> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,

    // new: lookup lists for building filter chips
    val colors: List<ColorEntity> = emptyList(),
    val seasons: List<SeasonEntity> = emptyList(),
    val occasions: List<OccasionEntity> = emptyList(),

    // new: active filter selections
    val selectedColorIds: Set<Long> = emptySet(),
    val selectedSeasonIds: Set<Long> = emptySet(),
    val selectedOccasionIds: Set<Long> = emptySet(),
    val favoritesOnly: Boolean = false,

    // new: derived badge count (sum of non-zero filter dimensions, not total values)
    val activeFilterCount: Int = 0,
)
```

`activeFilterCount` is the number of active filter *dimensions* (max 4: colors, seasons,
occasions, favorites) — drives the badge on the filter icon button.

### 1.2 Extend `ClosetViewModel`

**Add private filter flows:**
```kotlin
private val _selectedColorIds   = MutableStateFlow<Set<Long>>(emptySet())
private val _selectedSeasonIds  = MutableStateFlow<Set<Long>>(emptySet())
private val _selectedOccasionIds = MutableStateFlow<Set<Long>>(emptySet())
private val _favoritesOnly      = MutableStateFlow(false)
```

**Load lookup lists** — inject `LookupRepository` (already a `@Singleton`) alongside the
existing `ClothingRepository`:
```kotlin
lookupRepository.getColors()    // Flow<List<ColorEntity>>
lookupRepository.getSeasons()   // Flow<List<SeasonEntity>>
lookupRepository.getOccasions() // Flow<List<OccasionEntity>>
```

**Update `combine()` block** — mirror the `WardrobePickerViewModel` pattern, extending it
with the two additional dimensions:

```kotlin
// Inner combine: pack the four filter flows into one tuple to stay under the 5-flow limit
private val filterState = combine(
    _selectedCategoryId,
    _selectedColorIds,
    _selectedSeasonIds,
    combine(_selectedOccasionIds, _favoritesOnly) { occ, fav -> occ to fav }
) { catId, colorIds, seasonIds, (occIds, favOnly) ->
    FilterState(catId, colorIds, seasonIds, occIds, favOnly)
}
```

Filter predicate inside the outer combine:
```kotlin
val filtered = allItems
    .filter { fs.categoryId == null || it.item.categoryId == fs.categoryId }
    .filter { fs.colorIds.isEmpty() || it.colors.any { c -> c.id in fs.colorIds } }
    .filter { fs.seasonIds.isEmpty() || it.seasons.any { s -> s.id in fs.seasonIds } }
    .filter { fs.occIds.isEmpty() || it.occasions.any { o -> o.id in fs.occIds } }
    .filter { !fs.favOnly || it.item.isFavorite == 1 }
```

**Add public toggle/clear functions:**
```kotlin
fun toggleColorFilter(id: Long)    // add/remove from _selectedColorIds
fun toggleSeasonFilter(id: Long)   // add/remove from _selectedSeasonIds
fun toggleOccasionFilter(id: Long) // add/remove from _selectedOccasionIds
fun toggleFavoritesOnly()          // flip _favoritesOnly
fun clearAllFilters()              // reset all four flows to empty/false
```

**activeFilterCount** — compute inline in the combine block:
```kotlin
val activeFilterCount = listOf(
    fs.colorIds.isNotEmpty(),
    fs.seasonIds.isNotEmpty(),
    fs.occIds.isNotEmpty(),
    fs.favOnly
).count { it }
```

> **No DAO or Repository changes needed.** `getAllItemDetails()` already loads
> `colors`, `seasons`, and `occasions` via `@Relation`. All filtering is in-memory,
> consistent with the existing pattern.

---

## Phase 2 — Favorites Shortcut (standalone, high-value)

**Files:** `features/wardrobe/.../ClosetScreen.kt`

Add a Favorites chip to the existing `CategoryFilterRow` `LazyRow` — before "All Items":

```kotlin
FilterChip(
    selected = uiState.favoritesOnly,
    onClick = onToggleFavorites,
    label = { Text(stringResource(R.string.filter_favorites)) },
    leadingIcon = {
        Icon(
            imageVector = if (uiState.favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize)
        )
    }
)
```

This is a quick-access toggle that works independently of the filter panel — no bottom
sheet needed to reach it. String: `filter_favorites = "Favorites"`.

---

## Phase 3 — Filter Panel UI

**Files:**
- `features/wardrobe/.../FilterPanel.kt` — new file
- `features/wardrobe/.../ClosetScreen.kt` — filter icon button + bottom sheet wiring

### 3.1 `FilterPanel.kt` (new composable)

`ModalBottomSheet` with three chip sections:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterPanel(
    colors: List<ColorEntity>,
    seasons: List<SeasonEntity>,
    occasions: List<OccasionEntity>,
    selectedColorIds: Set<Long>,
    selectedSeasonIds: Set<Long>,
    selectedOccasionIds: Set<Long>,
    onToggleColor: (Long) -> Unit,
    onToggleSeason: (Long) -> Unit,
    onToggleOccasion: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
)
```

Layout (vertical scroll inside the sheet):

1. **Header row:** "Filters" title + "Clear all" `TextButton` (only enabled when any filter active)
2. **Colors section:** `SectionHeader("Color")` + `FlowRow` of color swatch `FilterChip`s
   - Each chip: 12 dp colored circle + color name, `selected = id in selectedColorIds`
3. **Seasons section:** `SectionHeader("Season")` + `FlowRow` of `FilterChip`s
4. **Occasions section:** `SectionHeader("Occasion")` + `FlowRow` of `FilterChip`s
5. **Bottom spacer** for nav bar insets

All sections are always visible (no collapsing). Empty sections (e.g., no colors seeded)
are hidden with `if (colors.isNotEmpty())`.

Use `ExperimentalLayoutApi` `FlowRow` for chip wrapping — it handles variable chip widths
cleanly and is already in the Material 3 dependency.

### 3.2 Filter button in `ClosetScreen`

Add to `TopAppBar` actions:

```kotlin
IconButton(onClick = { showFilterPanel = true }) {
    BadgedBox(
        badge = {
            if (uiState.activeFilterCount > 0) Badge { Text(uiState.activeFilterCount.toString()) }
        }
    ) {
        Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.cd_open_filters))
    }
}
```

Wire `FilterPanel` with `var showFilterPanel by remember { mutableStateOf(false) }`:

```kotlin
if (showFilterPanel) {
    FilterPanel(
        ...,
        onDismiss = { showFilterPanel = false }
    )
}
```

Pass ViewModel callbacks down through `ClosetContent` as lambdas (consistent with
existing pattern — no ViewModel reference below `ClosetScreen`).

---

## Phase 4 — Filtered Empty State

**Files:** `features/wardrobe/.../ClosetScreen.kt` (or shared `ClosetComponents.kt`)

Distinguish two empty states in `ClosetGrid` (or the containing column):

| Condition | Message | CTA |
|-----------|---------|-----|
| `items.isEmpty() && activeFilterCount == 0` | "Your wardrobe is empty" (existing) | Add item |
| `items.isEmpty() && activeFilterCount > 0` | "No items match your filters" | "Clear filters" TextButton → `onClearAll()` |

---

## New string resources

```xml
<!-- filter_strings.xml or strings.xml -->
<string name="filter_favorites">Favorites</string>
<string name="filter_title">Filters</string>
<string name="filter_clear_all">Clear all</string>
<string name="filter_section_color">Color</string>
<string name="filter_section_season">Season</string>
<string name="filter_section_occasion">Occasion</string>
<string name="filter_empty_results">No items match your filters</string>
<string name="filter_clear_filters">Clear filters</string>
<string name="cd_open_filters">Open filters</string>
```

---

## Delivery order

| # | What | Scope | Value |
|---|------|-------|-------|
| 1 | ViewModel + state foundation | ClosetViewModel.kt | Unblocks everything |
| 2 | Favorites chip | ClosetScreen.kt (~10 lines) | Immediately usable |
| 3 | FilterPanel composable | FilterPanel.kt (new) | Core feature |
| 4 | Filter button + sheet wiring | ClosetScreen.kt | Ties it together |
| 5 | Filtered empty state | ClosetScreen.kt (~15 lines) | Polish |

Each step compiles and ships independently. Steps 3 and 4 are the only ones that need
to land together (the panel is unreachable until the button is wired).

---

## What is intentionally excluded

- **Subcategory filter** — requires cascading UI (subcategory chips only appear when a
  parent category is selected). Defer until category filter UX is validated.
- **Material / Pattern / Brand filters** — low filtering utility for most wardrobes;
  add if user-requested.
- **Wash status / item status filters** — edge-case utility; the stats screen already
  surfaces wash status data.
- **Persisting filters across app restarts** — `DataStore` round-trip adds complexity;
  ViewModel lifetime is sufficient for a local-first app.
