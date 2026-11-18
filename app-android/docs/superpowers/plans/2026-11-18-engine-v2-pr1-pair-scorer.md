# Engine v2 PR 1: replay check + compatibility data + pair scorer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver PR 1 of engine v2 (#96). The PR does three things:
- **A replay check.** An offline hit-rate measurement, recorded first as a baseline on today's engine.
- **The compatibility signals.** Co-wear history, saved-outfit co-membership, an occasion-derived formality level and color hex all reach the engine.
- **A pure `PairScorer`.** Its pair compatibility feeds the *existing* assembly's outfit score.

Assembly (beam search) is PR 2. Embedding backoff is PR 3.

**Spec:** `docs/superpowers/specs/2026-11-17-recommendation-engine-v2-design.md` (approved #98, amended #104). Its "Pair score", "Formality mapping", "Testing" and "Delivery 1" sections bind this plan, except for the single deviation recorded in Architecture below.

**Architecture:** One PR, three tasks.
- **Task 1 (replay harness + baseline):** a pure jvmTest harness in `features/recommendations` with a synthetic planted-taste wardrobe and a hit-rate@10-by-core metric. The current engine's score is recorded as `BASELINE_HIT_RATE` and asserted not to regress. No production change.
- **Task 2 (data plumbing):**
  - `RecommendationDao` queries for co-wear pairs and item counts (from `outfit_log_items`), saved-outfit pairs (from `outfit_items`), item occasion names, and item color hexes.
  - `RecommendationRepository` wrappers, plus new `EngineInput` / `EngineItem` fields with neutral defaults.
  - The ViewModel fetches them in parallel with the existing signals.
  - No scoring change.
- **Task 3 (`PairScorer` + wiring):** a pure `engine/PairScorer.kt` (history lift with shrinkage, formality gap, hue harmony), wired into `scoreCombo`. Then the spec's behaviour tests, the replay "must improve" assertion, docs, and the PR checkpoint.

**Deviation from the spec's pair formula (owner OK needed at plan review).** The spec writes `pair = w·hist + (1 − w)·prior`. This plan instead uses `outfit = meanItem × rules × historyFactor`:
- `rules` is today's outfit-level color-harmony and pattern multipliers plus the new formality and hue terms.
- `historyFactor` is the geometric mean, over all item pairs, of `w·hist + (1 − w)·1.0`.
- **Why:**
  - With no history, `historyFactor = 1`, so cold start is exactly #95's behaviour. The 39 existing engine tests keep their values, rather than shifting by an approximate amount.
  - The rules stay at full weight, so a heavily worn pairing can't override a color clash or formality clash.
- **Cost:** a pair worn together very often still can't fully escape a rule penalty. `w ≤ W_MAX = 0.7` caps history's pull at about ±21% per pair either way.

**Tech Stack:** Kotlin 2.4.21, Room KMP, kotlinx-datetime, Koin. No new libraries.

## Global Constraints

- **The engine stays pure** (`commonMain`, no I/O, no clock). Inputs arrive in `EngineInput`; `today` already comes from `AppClock` via the ViewModel.
- **Constants** go in the engine companion: `K = 5.0`, `W_MAX = 0.7`, `PAIR_MIN = 0.7`, `PAIR_MAX = 1.3`, `SAVED_OUTFIT_WEIGHT = 0.5`, `FORMALITY_GAP_PENALTY = 0.85` (applied when `|a − b| ≥ 2`).
- **Formality levels** are exactly the spec table:
  - Loungewear, Athletic → 1
  - Casual, Outdoor/Hiking, Vacation → 2
  - Date Night → 3
  - Work/Business → 4
  - Formal, Special Occasion → 5

  An item's level is the median of its tagged occasions' levels. Unknown or user-created occasions are ignored, and an item with no known level gets no formality penalty.
- **Cold-start parity:** with no co-wear, no saved outfits, no occasions and no hex, every existing engine test passes with its current values. Existing fixtures have none of these, so their values are unchanged.
- **New `EngineInput` / `EngineItem` fields default to empty or null** (`EngineItem.colorHexes: List<String> = emptyList()`, `formalityLevel: Double? = null`; `EngineInput.coWear: CoWearStats = CoWearStats.EMPTY`), so existing call sites and tests compile untouched.
- **The `hist` properties** (spec), with `c` = weighted co-wear count, `E = nA·nB / N` the chance expectation, and `lift = (c + 1) / (E + 1)` clamped to `[PAIR_MIN, PAIR_MAX]`:
  - 1.0 with no evidence;
  - above 1 when a pair is worn together more often than chance;
  - below 1 when both items are worn often but never together.
- **Evidence and weight.** `n = max(c, E)` and `w = min(W_MAX, n / (n + K))`.
  - **Why `max(c, E)`:** a pair never worn together but expected often still earns weight, which gives the "below 1" case.
- **Saved outfits** add `SAVED_OUTFIT_WEIGHT` per co-membership to `c` and to each item's count. `N` = number of logs + 0.5 × number of saved outfits.
- **Hue harmony** applies only when both items have at least one hex and both are chromatic (HSL saturation ≥ 0.15 and lightness in [0.12, 0.88]; otherwise neutral, which has no effect):
  - hue difference ≤ 30° (analogous) → × 1.05;
  - 150°–210° (complementary) → × 1.05;
  - 60°–120° (clash) → × 0.90;
  - anything else → 1.0.

  When either item lacks a hex, today's family rule applies to that pair. Thresholds are the builder's per the spec, and these starting values are this plan's call.
- **Performance:** a jvmTest with a generated 500-item, 300-log wardrobe asserts that one `recommend(topN = 25, candidatesPerSlot = 3)` stays under a generous **1 s** on the JVM. It's a smoke guard, not a benchmark.
- **Test names:** Kotlin/Native rejects `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\` in backtick test names.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop daemons.
  - This machine runs low on memory, so prefer module-scoped tasks and run the full verification in chunks.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`, main and test.

## Review Focus

1. **Cold-start parity.** With empty new inputs, `recommend` returns identical combos and scores to today. (Task 3: the existing 39 + 22 engine tests pass unchanged, plus one explicit test comparing results with and without an all-empty `CoWearStats` on a varied fixture.)
2. **History works and stays bounded.** A pair logged together often outranks an otherwise-equal pair; a pair worn often apart ranks lower; `w` never exceeds `W_MAX`. (Task 3: `PairScorerTest` unit cases + an engine-level ranking test.)
3. **Echo-chamber guard.** With one dominant historical pair, Regenerate still surfaces other cores. (Task 3: a `RecommendationViewModelSessionTest` case with planted co-wear.)
4. **Replay check.** Hit-rate@10 by core doesn't drop below the baseline, and PR 1 raises it on the planted-taste fixture. (Task 1 records it; Task 3 asserts the improvement.)
5. **DAO correctness.** Pair counts are unordered and unique (`a < b`), each log counts once per pair, and only candidate items are counted. Saved outfits are counted separately. (Task 2: `RecommendationDaoTest` in `core/data` jvmTest against `TestDatabase`.)

---

### Task 1: Offline replay harness + baseline

**Files:**
- **Create:** `features/recommendations/src/jvmTest/.../engine/replay/PlantedTasteFixture.kt`, a deterministic generator (seeded `Random`):
  - about 60 items across tops, bottoms, footwear and outerwear, each with color families;
  - about 6 planted "favourite" (top, bottom, shoe) triples worn often;
  - about 120 logs over 180 days, 70% planted triples and 30% random valid outfits;
  - log dates ascending.
- **Create:** `.../engine/replay/ReplayHarness.kt`:
  - `fun hitRateAt10(fixture, engine, holdOut = 20): Double`. For each of the last `holdOut` logs, it builds `EngineInput` using **only earlier logs**: last-worn dates now; co-wear stats only once Task 2 adds the field. `today` is that log's date.
  - It runs `recommend(topN = 10, candidatesPerSlot = 3)` with no jitter and no exclusion, and counts a hit when the held-out outfit's core (top + bottom ids) is among the result cores.
- **Create:** `.../engine/replay/ReplayCheckTest.kt` with `BASELINE_HIT_RATE` (the value measured on the current engine, rounded down to 2 decimals) and `hit rate does not regress below the baseline`.

**Interfaces:** produces the fixture, `hitRateAt10`, and `BASELINE_HIT_RATE`. Task 3 adds the co-wear inputs to the harness and a second assertion.

- [ ] **Step 1:** Write the harness and fixture. Run the test with a placeholder baseline of 0.0 and record the measured value.
- [ ] **Step 2:** Set `BASELINE_HIT_RATE` to the measured value (rounded down) and re-run → PASS.
- [ ] **Step 3:** Verify `./gradlew :features:recommendations:jvmTest` passes and the iOS test compile is unaffected (jvmTest only).
- [ ] **Step 4:** Commit: `test(recommendations): offline replay harness with planted taste; baseline hit-rate@10 (#96)`.

### Task 2: Compatibility signals from the database to `EngineInput`

**Files:**
- **`core/data/.../dao/RecommendationDao.kt`:**
  - `getCoWearPairs(itemIds)`: `ItemPair(aId, bId, count)` from `outfit_log_items`, where `a < b`, both in `itemIds`, with `COUNT(DISTINCT outfit_log_id)`.
  - `getItemLogCounts(itemIds)`.
  - `getTotalLogCount()`.
  - `getSavedOutfitPairs(itemIds)` and `getSavedOutfitCount()` from `outfit_items`/`outfits`.
  - `getItemOccasionNames(itemIds)`: `(itemId, name)`.
  - `getItemColorHexes(itemIds)`: `(itemId, hex)`, non-null hex only.
- **`core/data/.../repository/RecommendationRepository.kt`:** `DataResult` wrappers in the existing style.
- **`features/recommendations/.../engine/EngineModels.kt`:**
  - `data class CoWearStats(pairCounts: Map<Pair<Long, Long>, Double>, itemCounts: Map<Long, Double>, total: Double) { companion object { val EMPTY } }`, keyed with the smaller id first;
  - `EngineItem.colorHexes`, `EngineItem.formalityLevel`;
  - `EngineInput.coWear`.
- **`features/recommendations/.../engine/Formality.kt`:** `internal fun formalityLevel(occasionNames: List<String>): Double?`, the spec table and median.
- **`RecommendationViewModel.fetchAndRun`:** fetch the new signals alongside the existing `async` reads, build `CoWearStats` (logs weight 1, saved outfits weight 0.5) and the new item fields.
- **Tests:**
  - `core/data/src/jvmTest/.../dao/RecommendationDaoCoWearTest.kt` (Review Focus 5);
  - `features/recommendations/src/commonTest/.../engine/FormalityTest.kt` (every table row, the median of mixed tags, unknown names ignored, no tags → null);
  - a ViewModel test that the fetched signals reach `EngineInput`, via a capturing engine or by checking repo calls.
- **Replay harness:** now pass co-wear stats built from earlier logs. The baseline is unchanged, because the engine doesn't read them yet.

**Interfaces:** produces `CoWearStats`, `EngineItem.colorHexes`, `EngineItem.formalityLevel`, `EngineInput.coWear`, `formalityLevel(...)`, and the DAO/repository methods.

- [ ] **Step 1:** Write the DAO and formality tests.
- [ ] **Step 2:** Run `./gradlew :core:data:jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - `./gradlew :core:data:jvmTest :features:recommendations:jvmTest test` passes;
  - the recommendations and core/data metadata compiles and iOS main/test compiles pass.
- [ ] **Step 5:** Commit: `feat(recommendations): co-wear, saved-outfit, formality and hex signals reach EngineInput (#96)`.

### Task 3: `PairScorer` wired into outfit scoring; docs; PR checkpoint

**Files:**
- **Create `features/recommendations/.../engine/PairScorer.kt`** (pure, internal):
  - `historyTerm(a, b, coWear): Double`, which returns `w·hist + (1 − w)`;
  - `formalityTerm(a, b): Double`;
  - `hueTerm(a, b): Double?`, which returns null when hue doesn't apply so the caller falls back to the family rule.
- **`OutfitRecommendationEngine.scoreCombo`:**
  - Color harmony: per non-neutral pair, use `hueTerm` when non-null, otherwise the existing family rule. Keep the geometric mean over those pairs.
  - Multiply by the geometric mean of `formalityTerm` over all item pairs.
  - Multiply by `historyFactor`, the geometric mean of `historyTerm` over all item pairs.
  - The layering, footwear and pattern multipliers are unchanged.
- **Tests:**
  - **`PairScorerTest`:**
    - no evidence → 1.0;
    - frequent co-wear → > 1, ≤ PAIR_MAX;
    - frequent-apart → < 1, ≥ PAIR_MIN;
    - `w` capped;
    - formality gap ≥ 2 → 0.85, gap < 2 → 1.0, unknown → 1.0;
    - hue: complementary / analogous / clash / achromatic (null).
  - **Engine tests:**
    - cold-start parity (Review Focus 1);
    - A+B logged together ranks above A+C;
    - a level-5 blazer with level-1 sweatpants ranks below the same blazer with level-4 trousers;
    - hex harmony vs. clash;
    - the 500-item performance guard.
  - **Session test:** the echo-chamber guard.
  - **`ReplayCheckTest`:** add `hit rate improves on planted taste` (the new rate is strictly greater than `BASELINE_HIT_RATE`), and keep the no-regression assertion.
- **Docs:**
  - the spec's Delivery 1 gets a "done in #…" note;
  - `features/recommendations` KDoc pipeline step 6 describes the new terms;
  - **CHANGELOG** `### Changed`: "**Smarter outfit pairing** — suggestions now learn which clothes you wear together, avoid mixing very dressy and very casual pieces, and use real color harmony when colors have shades set."

- [ ] **Step 1:** Write the tests.
- [ ] **Step 2:** Run `./gradlew :features:recommendations:jvmTest` and expect a failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - the full CLAUDE.md verification command passes, in chunks;
  - `iosCompileCheck` passes with cross-compilation;
  - the CI clock-reads check stays clean.
- [ ] **Step 5:** Commit: `feat(recommendations): pair scorer — co-wear history, formality and hue harmony in outfit scoring (#96)`.
- [ ] **Step 6:** PR checkpoint (controller).
  - Push `feat/engine-v2-pair-scorer`, open the PR (part of #96; it doesn't close it), and wait for CI.
  - Device smoke on the test device, full flavor, with the #102 demo wardrobe:
    - recommendations run, and Regenerate keeps varying cores;
    - an outfit that was logged several times tends to appear;
    - no crash;
    - nothing saved.

    Record the replay numbers (baseline → new) in the PR description.
