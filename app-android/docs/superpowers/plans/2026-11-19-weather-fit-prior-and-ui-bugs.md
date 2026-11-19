# Weather-Fit Prior (#106) & UI Bugfixes (#80, #103, #110) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a cold-start weather-fit prior from garment attributes (materials, warmth layer, subcategory) blended with empirical wear history via Empirical Bayes / Bühlmann credibility ($w = n / (n + K)$ with $K = 5$), alongside three focused UI bug fixes: Vico x-axis category labels on Stats (#80), Closet grid scroll reset on sort/filter changes (#103), and Category launcher shortcut `singleTop` category selection (#110).

**Architecture:**
1. **Issue #106 (Weather-Fit Prior):** Add a pure `WeatherFitPrior` in `features/recommendations/.../engine/WeatherFitPrior.kt` grounded in ASHRAE Standard 55 garment insulation (*clo*) ordering and Empirical Bayes / Bühlmann credibility shrinkage ($w = \frac{n}{n + K}, K = 5$).
   - **Attribute inputs:** `EngineItem` gains optional `subcategoryName: String? = null` and `materials: Set<String> = emptySet()` (defaulted so existing bare test fixtures remain backwards-compatible). Populated in `RecommendationViewModel` and `HistoricalStratumBuilder` using the existing `RecommendationDao.getItemSubcategoryHints` and `getItemMaterialHints` queries.
   - **Combo Shrinkage Model (Option A + B):**
     - **Step 1 (Shrinkage of Comfort Bounds & Suitability Rates — Bühlmann / Beta-Binomial):**
       - When an item has known weather-informative attributes (`prior != null`), its prior temperature comfort band $[L_0, U_0]$ and prior rain/wind suitability rates $\pi_{0,\text{rain}}, \pi_{0,\text{wind}} \in [0, 1]$ act as the $K = 5$ pseudocount baseline.
       - For $n \ge 0$ logged observations with $w = \frac{n}{n + 5}$:
         - **Temperature band:** When $n > 0$ temperature logs exist ($[p_{10}, p_{90}]$), the effective comfort bounds are shrunk toward the prior:
           $$L_{\text{eff}} = w \cdot p_{10} + (1 - w) \cdot L_0, \quad U_{\text{eff}} = w \cdot p_{90} + (1 - w) \cdot U_0$$
           (When $n = 0$, $[L_{\text{eff}}, U_{\text{eff}}] = [L_0, U_0]$). This prevents 1–4 logs at a single temperature from collapsing the item's comfort zone to a 1-degree point interval.
         - **Rain / Wind rates:** Under a Beta-Binomial conjugate prior with pseudocount $K = 5$ and prior rate $\pi_0$, the posterior suitability rate after $n$ logs with observed rate $\hat{p}$ is:
           $$p_{\text{eff}} = w \cdot \hat{p} + (1 - w) \cdot \pi_0$$
     - **Step 2 (Multiplier Evaluation & Credibility Blending):**
       - Let $m_{\text{prior}} \in \{\text{PENALTY}, 1.0\}$ be the prior multiplier evaluated against $[L_0, U_0]$ (or $\pi_0 < 0.20$), and let $m_{\text{post}} \in \{\text{PENALTY}, 1.0\}$ be the multiplier evaluated against the shrunk comfort zone $[L_{\text{eff}}, U_{\text{eff}}]$ (or $p_{\text{eff}} < 0.20$).
       - For $n = 0$ ($w = 0$), both coincide: $m = m_{\text{prior}}$.
       - For $n > 0$, the effective multiplier blends the posterior-bound decision and the pure empirical decision ($m_{\text{hist}}$ when $n \ge 5$, else $m_{\text{post}}$):
         $$m = w \cdot m_{\text{hist/post}} + (1 - w) \cdot m_{\text{post}}$$
         so at $n = 0$ the prior carries 100% of the signal, at $1 \le n < 5$ a single log cannot violently flip the item without crossing the cushioned $[L_{\text{eff}}, U_{\text{eff}}]$ band, and as $n \gg 5$ ($w \to 1$) the empirical history dominates completely.
       - **Backwards compatibility guard:** When an item has **no** weather-informative attributes (`subcategoryName == null && materials.isEmpty() && warmthLayer !in {"Base", "Mid", "Outer"}`), `WeatherFitPrior.forItem(item)` returns `null` and `OutfitRecommendationEngine.itemScore` falls back to the exact legacy threshold (`logCount >= 5`), preserving 100% of existing unit test scores.
2. **Issue #80 (Stats Wear by Category X-Axis Labels):**
   - In `features/stats/.../StatsActivityComponents.kt` (`CategoryWearSection`), Vico 3.0.3's default `AlignedHorizontalAxisItemPlacer` multiplies label spacing by `ceil(maxLabelWidth / xSpacing)`, causing long category names like `"Dresses & Jumpsuits"` to skip adjacent bars, while `rememberAxisLabelComponent()` defaults to `vicoTheme.textColor` instead of Material 3 `onSurfaceVariant`.
   - Fix by supplying `HorizontalAxis.ItemPlacer.segmented()` (places one label per bar segment without width-skipping), `labelRotationDegrees = -35f`, explicit `rememberAxisLabelComponent(style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp), lineCount = 1, overflow = TextOverflow.Ellipsis)` on both axes, and increasing chart height from `200.dp` to `230.dp`.
3. **Issue #103 (Closet Grid Scroll Reset on Sort/Filter Change):**
   - In `features/wardrobe/.../ClosetScreen.kt`, hoist `LazyGridState` via `rememberLazyGridState()` in `ClosetContent`, pass it to `ClosetGrid`, and use a `rememberSaveable` key tracking `(sortOrder, selectedCategoryId, favoritesOnly, showArchived, selectedColorIds, selectedSeasonIds, selectedOccasionIds, selectedSizeSystemIds)` so that changing sort or filters scrolls to index `0`, while initial composition and navigating back from `ClothingDetailScreen` preserve scroll position.
4. **Issue #110 (Category Launcher Shortcut `singleTop` Unfiltered Closet):**
   - In `shared/.../App.kt` and `features/wardrobe/.../ClosetViewModel.kt`, `ClosetDestination(initialCategoryId = id)` navigated with `launchSingleTop = true` reuses the existing start destination back-stack entry and its `ClosetViewModel`.
   - Fix by reading the latest `ClosetDestination` route from the `NavBackStackEntry` in `App.kt` (or observing `savedStateHandle.getStateFlow<Long?>("initialCategoryId", null)` in `ClosetViewModel`) and invoking `viewModel.selectCategory(id)` whenever a new shortcut category argument arrives.

**Tech Stack:** Kotlin Multiplatform (`commonMain`), Compose Multiplatform, Vico 3.0.3, Navigation Compose, Kotlin Test.

---

## Task 1: Pure `WeatherFitPrior` & Engine Integration (#106)

**Files:**
- Create: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/WeatherFitPrior.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/EngineModels.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/OutfitRecommendationEngine.kt`
- Modify: `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/EngineTestFixtures.kt`
- Create: `features/recommendations/src/commonTest/kotlin/com/closet/features/recommendations/engine/WeatherFitPriorTest.kt`

- [ ] **Step 1: Add `subcategoryName` and `materials` to `EngineItem`**
  - Update `EngineItem` in `EngineModels.kt` with defaulted properties:
    ```kotlin
    val subcategoryName: String? = null,
    val materials: Set<String> = emptySet(),
    ```
  - Update `item(...)` helper in `EngineTestFixtures.kt` to accept optional `subcategoryName: String? = null` and `materials: Set<String> = emptySet()`.

- [ ] **Step 2: Implement `WeatherFitPrior.kt`**
  - Define `data class ItemWeatherPrior(val tempLowC: Double, val tempHighC: Double, val rainSuitabilityPct: Double, val windSuitabilityPct: Double)`.
  - Map seeded subcategories (`DatabaseSeeder.seedSubcategories`), warmth layers (`Base`, `Mid`, `Outer`), and seeded materials (`DatabaseSeeder.seedMaterials`) to prior comfort bands and rain/wind suitability percentages:
    - **Subcategory base temperature bands `[lowC, highC]`:**
      - Cold (`[-10.0, 12.0]`): `"Coat"`
      - Cool-Cold (`[-5.0, 16.0]`): `"Sweater"`, `"Hoodie"`, `"Sweatshirt"`, `"Cardigan"`, `"Scarf"`, `"Tights"`, `"Jacket"`, `"Raincoat"`
      - Cool-Mild (`[0.0, 20.0]`): `"Vest"`, `"Boots"`, `"Track Jacket"`, `"Blazer"`, `"Joggers/Sweatpants"`
      - All-Around (`[5.0, 26.0]`): `"Shirt"`, `"Blouse"`, `"Jeans"`, `"Trousers/Slacks"`, `"Chinos"`, `"Leggings"`, `"Dress Shoes"`, `"Sneakers"`, `"Jumpsuit"`, `"Bodysuit"`
      - Warm (`[12.0, 30.0]`): `"T-Shirt"`, `"Polo"`, `"Dress"`, `"Skirt"`
      - Hot (`[18.0, 38.0]`): `"Tank Top"`, `"Shorts"`, `"Athletic Shorts"`, `"Sandals"`, `"Romper"`, `"Sports Bra"`, `"One-Piece"`, `"Bikini/Trunks"`, `"Rash Guard"`
    - **Warmth layer fallback (when subcategory has no specific band):**
      - `"Outer"` → `[-5.0, 16.0]`
      - `"Mid"` → `[0.0, 18.0]`
      - `"Base"` → `[5.0, 26.0]`
    - **Material thermal shift (averaged across mapped materials on the item):**
      - Warm insulators: `"Wool"` (`-6.0`), `"Cashmere"` (`-7.0`), `"Fleece"` (`-6.0`), `"Corduroy"` (`-4.0`), `"Velvet"` (`-4.0`), `"Suede"` (`-3.0`), `"Leather"` (`-3.0`)
      - Breathable hot-weather fabrics: `"Linen"` (`+6.0`), `"Chiffon"` (`+6.0`), `"Mesh"` (`+5.0`), `"Silk"` (`+2.0`), `"Rayon/Viscose"` (`+2.0`), `"Bamboo"` (`+2.0`), `"Modal"` (`+1.0`)
    - **Rain suitability prior rate $\pi_{0,\text{rain}} \in [0, 1]$ (threshold is `< 0.20`):**
      - Rain-unfriendly (`0.05` → triggers `< 0.20` penalty): subcategories `"Sandals"`, `"Slippers"`, or materials containing `"Suede"`, `"Silk"`, `"Cashmere"`, `"Velvet"`, `"Chiffon"`, `"Linen"` (unless subcategory is `"Raincoat"`).
      - Rain-ready / neutral (`0.50` → no penalty): `"Raincoat"` (`0.90`), `"Boots"` (`0.75`), `"Nylon"` / `"Polyester"` / `"Leather"` / `"Faux Leather"` outerwear (`0.70`), and standard garments (`0.50`).
    - **Wind suitability prior rate $\pi_{0,\text{wind}} \in [0, 1]$ (threshold is `< 0.20`):**
      - Wind-unfriendly (`0.05` → triggers `< 0.20` penalty): materials containing `"Chiffon"`, `"Silk"`, `"Linen"` (when not `"Outer"` layer), or subcategories `"Hat/Cap"`, `"Skirt"`, `"Sandals"`.
      - Wind-ready / neutral (`0.50` → no penalty): `"Outer"` warmth layer (`0.80`), `"Coat"` / `"Jacket"` / `"Raincoat"` (`0.80`), standard garments (`0.50`).
  - Expose blending helpers with `PRIOR_WEIGHT_K = 5.0`:
    - `temperatureMultiplier(prior: ItemWeatherPrior?, percentiles: ItemTempPercentiles?, forecastLow: Double, forecastHigh: Double): Double`
    - `rainMultiplier(prior: ItemWeatherPrior?, rain: ItemRainSuitability?): Double`
    - `windMultiplier(prior: ItemWeatherPrior?, wind: ItemWindSuitability?): Double`

- [ ] **Step 3: Wire `WeatherFitPrior` into `OutfitRecommendationEngine.itemScore`**
  - Replace the hard `logCount >= MIN_LOGS_FOR_SIGNAL` checks in `OutfitRecommendationEngine.itemScore` with `WeatherFitPrior.temperatureMultiplier`, `WeatherFitPrior.rainMultiplier`, and `WeatherFitPrior.windMultiplier`.
  - Write unit tests in `WeatherFitPriorTest.kt` verifying:
    1. With zero logs, a wool sweater scores below a linen shirt on a hot day (`26–32 °C`) and above it on a cold day (`-2–4 °C`).
    2. With 1–2 logs (e.g. a single wear at `10 °C`), the comfort zone is cushioned by the prior (Option A) and the multiplier is blended by $w = n / (n + 5)$ (Option B).
    3. With plenty of logs ($n = 20$), the learned range dominates even when it contradicts the prior.
    4. Bare items without attributes keep exact legacy behavior (`< 5` logs skipped, `>= 5` logs full penalty).

---

## Task 2: Wire Subcategory & Material Inputs in ViewModel and Stratum Builder (#106)

**Files:**
- Modify: `core/data/src/commonMain/kotlin/com/closet/core/data/repository/RecommendationRepository.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/RecommendationViewModel.kt`
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/learning/HistoricalStratumBuilder.kt`

- [ ] **Step 1: Expose material names set and subcategory names from `RecommendationRepository`**
  - Add `getItemSubcategoryNames(itemIds: List<Long>): DataResult<Map<Long, String>>` and `getItemMaterialNames(itemIds: List<Long>): DataResult<Map<Long, Set<String>>>` to `RecommendationRepository` backed by the existing `dao.getItemSubcategoryHints(itemIds)` and `dao.getItemMaterialHints(itemIds)` queries.
- [ ] **Step 2: Populate `subcategoryName` and `materials` on `EngineItem`**
  - In `RecommendationViewModel.runEnginePipeline`, fetch subcategory names and material sets in parallel alongside the other item attributes and pass them into `EngineItem(...)`.
  - In `HistoricalStratumBuilder.getTrainingItems`, fetch subcategory names and material sets for all training items so historical choice strata use the exact same weather-fit prior at cold-start/early logs.

---

## Task 3: Fix Stats "Wear by Category" X-Axis Labels (#80)

**Files:**
- Modify: `features/stats/src/commonMain/kotlin/com/closet/features/stats/StatsActivityComponents.kt`

- [ ] **Step 1: Update `CategoryWearSection` axis configuration**
  - Configure ` axisLabel = rememberAxisLabelComponent(style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp), lineCount = 1, overflow = TextOverflow.Ellipsis)`.
  - Pass `label = axisLabel` to `VerticalAxis.rememberStart(label = axisLabel)` and `HorizontalAxis.rememberBottom(...)`.
  - On `HorizontalAxis.rememberBottom`, set `label = axisLabel`, `labelRotationDegrees = -35f`, and `itemPlacer = remember { HorizontalAxis.ItemPlacer.segmented() }`.
  - Increase chart height to `230.dp` so angled category labels render cleanly without clipping.

---

## Task 4: Fix Closet Grid Scroll Position on Sort & Filter Change (#103)

**Files:**
- Modify: `features/wardrobe/src/commonMain/kotlin/com/closet/features/wardrobe/ClosetScreen.kt`

- [ ] **Step 1: Hoist `LazyGridState` and reset scroll on sort/filter change**
  - Hoist `val gridState = rememberLazyGridState()` in `ClosetContent` and pass `gridState` into `ClosetGrid`.
  - Compute a serializable/saveable key of the active sort & filter state:
    `val sortAndFilterKey = remember(sortOrder, selectedCategoryId, favoritesOnly, showArchived, selectedColorIds, selectedSeasonIds, selectedOccasionIds, selectedSizeSystemIds) { ... }`
  - Store `var lastSortAndFilterKey by rememberSaveable { mutableStateOf(sortAndFilterKey) }`.
  - Inside `LaunchedEffect(sortAndFilterKey)`, if `lastSortAndFilterKey != sortAndFilterKey`, update `lastSortAndFilterKey = sortAndFilterKey` and call `gridState.scrollToItem(0)`.

---

## Task 5: Fix Category Launcher Shortcut `singleTop` Selection (#110)

**Files:**
- Modify: `features/wardrobe/src/commonMain/kotlin/com/closet/features/wardrobe/ClosetViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/closet/shared/App.kt`
- Modify: `features/wardrobe/src/jvmTest/kotlin/com/closet/features/wardrobe/ClosetViewModelTest.kt` (or create if needed)

- [ ] **Step 1: Observe `initialCategoryId` changes on `SavedStateHandle` & route entry**
  - In `ClosetViewModel`, observe `savedStateHandle.getStateFlow<Long?>("initialCategoryId", _selectedCategoryId.value)` (or handle incoming route argument updates) so whenever a new non-null `initialCategoryId` is delivered to the existing back-stack entry via `launchSingleTop`, `_selectedCategoryId.value = categoryId` is applied.
  - Also in `App.kt` inside `composable<ClosetDestination> { backStackEntry -> ... }`, read the route/arguments or pass `backStackEntry` so warm and cold shortcut launches reliably update the active category filter.
  - Add a unit test in `ClosetViewModelTest` verifying that updating `initialCategoryId` on `SavedStateHandle` (and calling `selectCategory`) updates `uiState.value.selectedCategoryId` and filters `uiState.value.items`.
