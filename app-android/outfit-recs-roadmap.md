# Outfit Recommendations — Actionable Roadmap

Reference: `outfit-recs-overview.md`

---

## Phase 1 — Programmatic Pipeline

### Schema & data foundation

- [x] **Migration 2→3** — add `categories.outfit_role` (Top/Bottom/OnePiece/Outerwear/Footwear/Accessory/Other) and `colors.color_family` (Neutral/Earth/Cool/Warm/Bright) with backfill seeds
- [x] Update `CategoryEntity` and `ColorEntity` Room entities to reflect new columns
- [x] Update `DatabaseSeeder` to seed `outfit_role` on all categories and `color_family` on all colors
- [x] Write migration test for Migration 2→3

---

### Module scaffolding

- [ ] Create `features/recommendations/` module (mirror structure of `features/stats/`)
- [ ] Wire module into `settings.gradle.kts` and `app/build.gradle.kts`
- [ ] Set up Hilt `RecommendationModule` in `features/recommendations/di/`

---

### Data layer — engine inputs

- [ ] Define `RecommendationRequest` data class (occasion: `Long?`, weather: `WeatherConditions?`)
- [ ] Define `WeatherConditions` parameter object (tempLowC, tempHighC, isRaining, isWindy) — engine receives this directly, never calls `WeatherRepository` itself
- [ ] Add `RecommendationDao` with hard-filter candidate query (Active + Clean + season match + optional occasion)
- [ ] Add suitability score queries to `RecommendationDao`:
  - [ ] Temp percentile query (10th–90th of logged `temperature_low`/`temperature_high` per item)
  - [ ] Rain% query (% of logs where `precipitation_mm` > 1.0, per item)
  - [ ] Wind% query (% of logs where `wind_speed_kmh` > 30, per item)
  - [ ] Last-worn date query per item (for tie-breaking only, not scoring)
- [ ] Add `RecommendationRepository` wrapping DAO calls in `DataResult`

---

### Engine — core logic

- [ ] `OutfitRecommendationEngine` — pure class, no Android/Hilt dependencies (unit-testable)
- [ ] Hard filter step (clean, active, season/occasion)
- [ ] Per-item suitability scoring (multiplicative multipliers; skip signal if < 5 logs)
- [ ] Category completeness: build valid combos — (Top + Bottom) OR (OnePiece), optional Outerwear/Footwear/Accessory
- [ ] Per-category cap: 1–2 candidates per slot before combining
- [ ] Color deduplication: one item per color+subcategory combo
- [ ] Layering validation (warmth_layer): enforce/encourage Outer on cold days; down-rank on warm days
- [ ] Color harmony scoring (color_family pairwise outfit adjustment)
- [ ] Pattern mixing scoring (solid/patterned classification + outfit-level bonus/penalty)
- [ ] Outfit score = mean of item scores × outfit-level multipliers; tie-break by last-worn date
- [ ] Return top 3 ranked combos

---

### Engine — unit tests

- [ ] Hard filter: items failing wash/status/season/occasion are excluded
- [ ] Suitability scoring: correct multipliers applied; sparse data skips signal
- [ ] Category completeness: only valid Top+Bottom or OnePiece combos formed
- [ ] Layering: cold day without Outer scores lower; warm day with Outer scores lower
- [ ] Color harmony: same-family bonus, Bright+Bright penalty, Warm+Cool penalty
- [ ] Pattern mixing: 1 patterned item bonus; 2+ patterned penalty
- [ ] Top 3 returned; tied scores broken by last-worn

---

### ViewModel

- [ ] `RecommendationViewModel` (`@HiltViewModel`) with `StateFlow<RecommendationUiState>`
- [ ] States: `Idle → OccasionSheet → WeatherSheet → Loading → Results(combos) → Error`
- [ ] Handle occasion selected / skipped
- [ ] Handle weather confirmed / skipped / auto-filled from `WeatherRepository`
- [ ] Handle regenerate (re-runs engine, replaces all 3 combos)
- [ ] Handle "Log it" (passes selected combo IDs to outfit logging flow)
- [ ] Handle "Save for later" (creates a named outfit from combo)

---

### UI — pre-suggestion sheets

- [ ] `OccasionSheet` — single-select bottom sheet, skippable; uses existing `OccasionEntity` list from `LookupDao`
- [ ] `WeatherSheet` — bottom sheet with:
  - [ ] Temp range inputs (low/high °C)
  - [ ] Condition picker (Sunny / Cloudy / Rainy / Snowy / Windy)
  - [ ] Precipitation toggle
  - [ ] Wind toggle
  - [ ] Auto-fill from `WeatherRepository` cache on open; show "Pulled from location data" chip when auto-filled
  - [ ] All fields user-overridable; sheet submittable with partial data

---

### UI — suggestions screen

- [ ] `RecommendationScreen` composable (Idle/Loading/Results/Error states)
- [ ] Horizontal pager carousel — 3 cards, page indicators
- [ ] `OutfitComboCard` composable — grid of item images (or placeholder) for the combo
- [ ] "Log it" action — navigates to outfit logging with combo pre-loaded
- [ ] "Save for later" action — saves combo as outfit, shows confirmation snackbar
- [ ] "Regenerate" action — triggers full re-run for all 3 slots

---

### Outfit gallery & entry point

- [ ] Restyle outfit gallery to 3-column grid (currently 1-column list)
- [ ] Expand existing FAB into a two-option menu: **Add outfit** + **Get suggestions**
- [ ] Wire "Get suggestions" → occasion sheet → weather sheet → `RecommendationScreen`

---

### Navigation

- [ ] Define `RecommendationDestination` (`@Serializable`) in `app/navigation/`
- [ ] Add `RecommendationNavigation.kt` extension in `features/recommendations/`
- [ ] Register route in `ClosetNavGraph.kt`

---

## Phase 2 — AI Coherence Scorer

> Start only after Phase 1 is solid. The programmatic pipeline must work independently.

### AI infrastructure

- [ ] Define `AiInferenceClient` interface (provider abstraction, mirroring weather's `WeatherServiceClient`)
- [ ] Implement `MlKitAiInferenceClient` (MLKit GenAI Prompt API)
- [ ] `AiPreferencesRepository` — stores `aiReady: Boolean` and `tokenLimit: Int` in DataStore

---

### Settings — AI section

- [ ] Add AI section to existing Settings screen (toggle off by default)
- [ ] Background worker on toggle-on: `checkStatus()` → `download()` (stream progress) → `getTokenLimit()` → set `aiReady = true`
- [ ] UI status states: Checking / Downloading (n%) / Ready / Failed / Not Supported
- [ ] Flip toggle back off on failure or unsupported device

---

### Coherence scorer

- [ ] `GeminiNanoCoherenceScorer` — wraps `AiInferenceClient`
- [ ] Build `PromptPrefix` (cached system instructions: output format, constraints, schema)
- [ ] Serialize candidate payload as dynamic suffix (include suitability scores as context hints)
- [ ] `countTokens()` gate before inference; trim payload if over limit
- [ ] Treat `FinishReason.MAX_TOKENS` as failed inference
- [ ] Validate `selected_ids` against candidate list before any DB interaction
- [ ] Discard silently on JSON parse failure or unknown IDs; fall back to programmatic top 3
- [ ] Surface `reason` field behind a "why?" tap (hidden by default)

---

### Wire scorer into engine

- [ ] Engine checks `aiReady` flag before invoking scorer
- [ ] If not ready: return top-3 programmatic result with no AI label, no "why?" affordance
- [ ] If ready: pass trimmed candidate payload to scorer; return re-ranked top 3

---

## Deferred

- [ ] Thumbs up/down feedback loop (requires new `recommendation_feedback` table)
- [ ] Per-item suitability scores surfaced in item detail view
- [ ] `general notes` fed as context to Nano
- [ ] Category & subcategory auto-fill from clothing image (separate AI ticket)
