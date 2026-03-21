# Stats Screen Roadmap

## What already exists

| Layer | Status |
|---|---|
| `StatsDao` | ✅ 3 queries: `getStatsOverview`, `getMostWornItems`, `getBreakdownByCategory` |
| `StatsRepository` | ✅ Thin wrapper, `@Singleton`, injected via Hilt |
| Feature module | ❌ Missing |
| ViewModel | ❌ Missing |
| Screen / nav entry | ❌ Missing |
| Bottom bar tab | ❌ Missing |

---

## Phase 1 — Data layer gaps

Queries to add to `StatsDao` / `StatsRepository` before building the screen.

- **Cost-per-wear ranking** — items with a `purchase_price`, ordered by
  `purchase_price / wear_count` ascending (cheapest per wear first); exclude
  never-worn items
- **Total outfits logged** — `COUNT(DISTINCT outfit_logs.id)` all-time, with
  optional `fromDate` filter
- **Wear frequency by category** — like `getBreakdownByCategory` but counting
  wear log entries rather than item counts; join through `outfit_log_items`
  (snapshot table) not `outfit_items`
- **Never-worn items list** — `getStatsOverview` already counts them; add a
  query that returns the actual `StatItem` rows so the screen can list them

---

## Phase 2 — Feature module scaffold

Mirrors the existing `features/outfits` and `features/wardrobe` structure.

1. Create `features/stats/` module with `build.gradle.kts`
   - Same deps as `features/outfits`: Compose BOM, Material3, Hilt, lifecycle,
     Coil, Timber, `project(":core:data")`, `project(":core:ui")`
2. `StatsNavigation.kt`
   - `@Serializable object StatsRoute`
   - `fun NavGraphBuilder.statsScreen()` extension
3. Wire into `ClosetNavGraph`
   - Add `StatsRoute` to `topLevelRoutes` with a chart icon and `nav_stats`
     string resource
   - Register `statsScreen()` in the `NavHost`

---

## Phase 3 — ViewModel

`StatsViewModel` combining all stat flows via `combine`.

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

`selectedPeriod` drives the `fromDate` passed to all filterable queries via
`flatMapLatest`. Changing the period chip re-subscribes all flows at once.

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
