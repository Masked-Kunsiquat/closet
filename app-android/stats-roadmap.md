# Stats Screen Roadmap

## What already exists

| Layer | Status |
|---|---|
| `StatsDao` | ✅ 7 queries: `getStatsOverview`, `getMostWornItems`, `getBreakdownByCategory`, `getCostPerWear`, `getTotalOutfitsLogged`, `getWearFrequencyByCategory`, `getNeverWornItems` |
| `StatsRepository` | ✅ Thin wrapper, `@Singleton`, injected via Hilt — all 7 queries exposed |
| Feature module | ✅ `features/stats/` — build.gradle.kts, StatsNavigation.kt, placeholder StatsScreen.kt |
| ViewModel | ✅ `StatsViewModel` — `StatPeriod` enum, `StatsUiState`, `flatMapLatest` on period |
| Screen / nav entry | ✅ `StatsRoute` registered in `ClosetNavGraph` |
| Bottom bar tab | ✅ Bottom nav bar added (Closet / Outfits / Stats) |

---

## Phase 1 — Data layer gaps ✅

All four queries added to `StatsDao` and exposed via `StatsRepository`.

- **Cost-per-wear ranking** ✅ — `getCostPerWear(fromDate)` → `Flow<List<CostPerWearItem>>`; excludes never-worn and null-price items
- **Total outfits logged** ✅ — `getTotalOutfitsLogged(fromDate)` → `Flow<Int>`
- **Wear frequency by category** ✅ — `getWearFrequencyByCategory(fromDate)` → `Flow<List<BreakdownRow>>`; joins `outfit_items → outfit_logs` (no snapshot table in Android schema)
- **Never-worn items list** ✅ — `getNeverWornItems()` → `Flow<List<StatItem>>`

---

## Phase 2 — Feature module scaffold ✅

1. `features/stats/build.gradle.kts` ✅ — mirrors `features/outfits` + Timber
2. `StatsNavigation.kt` ✅ — `@Serializable object StatsRoute`, `navigateToStats()`, `statsScreen(onItemClick)`
3. `StatsScreen.kt` ✅ — placeholder composable (full layout in Phase 4)
4. Wired into `ClosetNavGraph` ✅
   - `TopLevelRoute` data class + `topLevelRoutes` list (Closet / Outfits / Stats)
   - `NavigationBar` added to `Scaffold`; hidden on sub-screens
   - `statsScreen(onItemClick)` registered in `NavHost`
   - `Scaffold` moved from `MainActivity` into `ClosetNavGraph`

---

## Phase 3 — ViewModel ✅

`StatsViewModel` + `StatPeriod` + `StatsUiState` in `StatsViewModel.kt`.
`StatsScreen` wired to collect `uiState` via `collectAsStateWithLifecycle`.

```
UiState fields:
  overview        : StatsOverview           — total items, worn %, total value
  mostWorn        : List<StatItem>          — top N by wear count
  costPerWear     : List<CostPerWearItem>   — ranked cheapest-per-wear first
  categoryCount   : List<BreakdownRow>      — items per category
  categoryWear    : List<BreakdownRow>      — wear logs per category
  totalLogsCount  : Int
  neverWorn       : List<StatItem>
  selectedPeriod  : StatPeriod              — ALL_TIME | LAST_30 | LAST_90 | THIS_YEAR
```

`_selectedPeriod` (MutableStateFlow) drives `flatMapLatest` over the five
period-sensitive queries. `getCategoryBreakdown` and `getNeverWornItems` are
period-independent and folded in by the outer `combine`.

---

## Phase 4 — Screen layout

Single `verticalScroll` column with distinct sections (no nested lazy lists —
wear history is bounded in practice and the screen already scrolls).

1. **Period selector** — chip row: All time / 30 days / 90 days / This year
2. **Headline cards** — 3-up grid: total items · worn % · total wardrobe value
3. **Most worn** — horizontal lazy row of item thumbnails with wear count badge;
   taps navigate to item detail
4. **Cost per wear** — ranked list: item thumbnail + name + formatted
   cost-per-wear; null purchase price shown as `—`
5. **Total logs** — single stat callout: "You've logged N outfits"
6. **Wear by category** — ranked label + count list (or simple bar chart)
7. **Never worn** — collapsible section; taps navigate to item detail

---

## Phase 5 — Polish

- Empty state when the wardrobe has no items or no logs yet
- Image resolution via `StorageRepository.getFile()` (same pattern as other
  screens) passed into Coil for Most Worn and Cost Per Wear thumbnails
- Accessibility: `contentDescription` on stat values so screen readers can
  announce them meaningfully

---

## Suggested build order

```
Phase 1 (data gaps)
  → Phase 2 (module + nav scaffold)
    → Phase 3 (ViewModel)
      → Phase 4a (headline cards + most worn — enough to be useful)
        → Phase 4b (remaining sections)
          → Phase 5 (polish)
```

Phase 1 is the only real unknown — the wear-by-category join through
`outfit_log_items` may need care. Everything else follows patterns already
established in the codebase.
