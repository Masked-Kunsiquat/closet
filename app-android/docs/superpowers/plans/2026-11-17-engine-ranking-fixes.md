# Recommendation engine ranking fixes (#94, PR 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the ranking problems found in #94.
- "Regenerate" stops returning the same outfits.
- Results stop being near-duplicates.
- Colour-harmony scores stop compounding with outfit size.
- Shoe-less outfits stop winning when footwear exists.
- Items not worn in a while get a nudge.

**Architecture:** Two tasks.

**Task 1: the pure engine** (`features/recommendations/.../engine/`)
- Harmony becomes the geometric mean of the pair multipliers.
- A footwear-missing penalty.
- A wear-recency multiplier, which needs an optional `today` in `EngineInput`.
- Optional exclusion of already-shown combos.
- Optional ranking-only jitter, from an injected `Random`.
- Diversity re-ranking: no two results share a core.

**Task 2: `RecommendationViewModel`**
- It remembers the combos shown in the current suggestion session and excludes them on Regenerate. When everything has been shown, it starts the cycle over.
- It seeds the first run of each day from the date.
- It passes `today`.

Every new engine input defaults to "off". Callers that don't opt in, which includes all existing tests, get today's behavior, apart from the deliberate scoring changes listed under Global Constraints.

**Tech Stack:** Kotlin 2.4.21 (commonMain), kotlinx-datetime 0.8.0, `kotlin.random.Random`, kotlin.test.

**Spec:** issue #94, "PR 1: ranking fixes". The reference model for this plan's shape is `docs/superpowers/plans/2026-11-17-kmp-phase5-recommendations.md`.

## Global Constraints

- **The engine stays pure:** no I/O, no clock reads and no `Random.Default` inside the engine. Time and randomness come in through parameters.
- **Deliberate scoring changes.** Exact values, applied as multipliers on the outfit score:
  - **Harmony.** `colorHarmonyMultiplier` returns the **geometric mean** of the pair multipliers, `(∏ pair)^(1/pairCount)`, instead of their product. With one pair, which covers every existing 2-item harmony test, the value is unchanged.
  - **Footwear.** `MULTIPLIER_NO_FOOTWEAR = 0.85` applies when the combo has no `Footwear` item **and** the capped candidate pool has at least one `Footwear` item.
  - **Recency.** This is a per-item multiplier, applied only when `input.today != null` and the item has a parseable last-worn date `d`:
    - `today - d` ≤ 3 days → × `0.90` (`MULTIPLIER_WORN_RECENTLY`, `RECENT_WEAR_DAYS = 3`)
    - `today - d` ≥ 30 days → × `1.05` (`BONUS_NOT_WORN_LATELY`, `STALE_WEAR_DAYS = 30`)
    - Otherwise, a never-worn item, or a null `today` → × 1.0.

    The last-worn date is parsed from the first 10 characters (`YYYY-MM-DD`). An unparseable date counts as never worn.
- **Ranking-only jitter.** When a `jitter: Random` is passed:
  - each combo's **sort key** is `score × (1 + u)`, with `u` uniform in `[-0.03, 0.03]` drawn from that `Random`;
  - `OutfitCombo.score` keeps the un-jittered value;
  - with no `jitter`, ordering is exactly today's: score descending, then the last-worn tie-break.
- **Exclusion.** `exclude: Set<Set<Long>>` holds combo keys, where a key is the set of the combo's item IDs. Matching combos are dropped before ranking.
- **Diversity.**
  - **Core definition:** the core is the Top + Bottom IDs, or the OnePiece ID.
  - **Primary pass:** after sorting, take combos greedily, skipping any whose core already appears in the picks, until `topN`.
  - **Fill pass:** if there aren't enough, fill the remaining slots from the skipped combos in sorted order.
- **Session cycle (ViewModel).**
  - **What counts as one session:** a new occasion/weather confirmation starts a fresh session; Regenerate continues the current one.
  - **Exclusion:** the engine is called with every combo key shown so far in the session.
  - **Wrap-around:** if that returns no combos but an unexcluded run would return some, clear the session set and use the unexcluded result. The user sees the cycle restart instead of "no results".
  - **Jitter:** the first run of a session uses `Random(seed = today.toEpochDays())`, so the morning pick changes daily but is stable within a day. Regenerate uses an unseeded `Random`.
- **Unchanged:** the AI scorer contract. The AI path gets the pool from the same engine call, so exclusion and diversity apply to it too.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files", stop and report.
  - Also compile the iOS **test** sources locally: `-Pkotlin.native.enableKlibsCrossCompilation=true`.
  - Kotlin/Native rejects `()`, `.`, `:` etc. in backtick test names.

## Review Focus

1. **Regenerate never repeats while untried combos exist, then cycles.** With 4 possible combos and `topN = 3`, Regenerate shows the 4th plus 2 earlier ones, never an empty screen. (Task 2 test.)
2. **Existing exact-score tests still pass unchanged**, except any that combine 3+ non-neutral coloured items. Those change under the geometric mean; update only those, and justify each in the report. (Task 1.)
3. **Diversity doesn't drop valid outfits.** When there are fewer distinct cores than `topN`, the fill pass still returns up to `topN`. (Task 1 test.)
4. **Recency date edge cases:**
   - worn exactly 3 days ago → penalized;
   - worn exactly 30 days ago → bonus;
   - 4 or 29 days ago → neutral;
   - a malformed date or a date in the future → neutral, with no crash.

   (Task 1 tests.)
5. **The jitter changes order only.** `OutfitCombo.score` values equal the un-jittered scores, and the same seed gives the same order. (Task 1 test.)

---

### Task 1: Engine — harmony, footwear, recency, exclusion, jitter, diversity

**Files:**
- Modify `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/OutfitRecommendationEngine.kt`.
- Modify `.../engine/EngineModels.kt`: add `today: kotlinx.datetime.LocalDate? = null` as the last `EngineInput` field, so existing constructors compile.
- Update the class KDoc pipeline list to describe the new steps.
- Tests: `features/recommendations/src/commonTest/.../engine/OutfitRecommendationEngineTest.kt`, plus a new `.../engine/EngineRankingTest.kt` for the new behavior.

**Interfaces:**
- Produces `fun recommend(input: EngineInput, topN: Int = TOP_N, candidatesPerSlot: Int = CANDIDATES_PER_SLOT, exclude: Set<Set<Long>> = emptySet(), jitter: Random? = null): List<OutfitCombo>`.
- Produces `fun OutfitCombo.key(): Set<Long> = items.map { it.id }.toSet()`, public in the engine package, for the ViewModel.

- [ ] **Step 1: Write the failing tests** (`EngineRankingTest`, kotlin.test, using `OutfitRecommendationEngineTest`'s item-builder helpers; copy or extract them into a small shared `EngineTestFixtures.kt` if needed):
  - **Harmony:** 3 non-neutral items, all `Warm`, give 3 pairs of 1.10; their geometric mean is 1.10, so `score == 1.10 ± 0.001`. Before this change it was 1.331.
  - **Footwear:** with one Top, one Bottom and one Footwear item, the combo with shoes ranks first. The shoe-less Top+Bottom combo scores `0.85 × its base`.
  - **No footwear in the pool:** no penalty (score 1.0).
  - **Recency**, with `today = LocalDate(2026, 3, 31)` and a single-OnePiece combo:
    - last worn 2026-03-28 (3 days) → 0.90
    - 2026-03-27 (4 days) → 1.0
    - 2026-03-02 (29 days) → 1.0
    - 2026-03-01 (30 days) → 1.05
    - never worn → 1.0
    - `"not-a-date"` → 1.0
    - 2026-04-05 (future) → 1.0
    - `today = null` → 1.0
  - **Exclusion:** excluding the top combo's key → it's absent, and the next-best combo is first.
  - **Diversity:**
    - 2 tops × 1 bottom + 2 footwear gives 2 cores and 6 combos. `topN = 2` → 2 results with **different** cores.
    - With `topN = 4`, all 4 slots fill: 2 distinct cores, then 2 filled from the skipped combos.
  - **Jitter:** with `jitter = Random(42)`, the scores match a no-jitter run (as a set of `key → score`), and `Random(42)` twice gives the identical order.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :features:recommendations:jvmTest`; expect a compile failure (the new parameters don't exist yet).
- [ ] **Step 3: Implement.**
  - **Constants:** add the new ones to the companion object.
  - **Recency:** apply it in `scoreItem` (it runs regardless of weather, so put it before the `weather ?: return` early exit). Use `LocalDate.parseOrNull`-style parsing via `runCatching { LocalDate.parse(s.take(10)) }.getOrNull()`, and `daysUntil` for the day count.
  - **Footwear:** `scoreCombo` needs to know whether footwear exists in the pool, so pass `hasFootwearInPool: Boolean` from `recommend`.
  - **Pipeline order:** exclusion before sorting, then sorting (with the jittered key when `jitter` is given), then diversity selection, then `take`.
  - **Existing tests:** run the old suite. If an existing assertion changes, it must be one of the cases in Review Focus 2; update it with the new expected value and a one-line comment saying why.
- [ ] **Step 4: Verify**
  - `./gradlew :features:recommendations:jvmTest :features:recommendations:compileTestKotlinIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** with message `feat(recommendations): engine ranking — harmony mean, footwear, recency, exclusion, jitter, diverse cores (#94)`.

### Task 2: ViewModel session cycle, daily seed, today; PR checkpoint

**Files:**
- Modify `features/recommendations/src/commonMain/.../RecommendationViewModel.kt`.
- Tests: `features/recommendations/src/jvmTest/.../RecommendationViewModelSessionTest.kt`, reusing the mock setup of `RecommendationViewModelImageTest`.
- `CHANGELOG.md`: under `[Unreleased]` → `### Changed`, add **"Better outfit suggestions** — Regenerate shows new outfits instead of repeating, results no longer repeat the same top and bottom, outfits with shoes are preferred, and clothes you haven't worn in a while get a nudge."

**Interfaces:**
- Consumes Task 1's `recommend(..., exclude, jitter)`, `EngineInput.today` and `OutfitCombo.key()`.
- Adds the private state `shownComboKeys: MutableSet<Set<Long>>` in the ViewModel.
  - Cleared when a new session starts, from `onWeatherConfirmed` / `onWeatherSkipped`.
  - Kept on `onRegenerate`.
  - After each successful run, every returned combo's key is added.
- Today comes from `Clock.System.todayIn(TimeZone.currentSystemDefault())`. Read it once per run and pass it to both `EngineInput.today` and the seed.

- [ ] **Step 1: Write the failing tests** (`RecommendationViewModelSessionTest`; repository stubs return a fixed wardrobe with exactly 4 valid combos at the engine's default `topN = 3`; AI off; real `OutfitRecommendationEngine`; `Dispatchers.Main` set as in the other ViewModel tests):
  - Confirm weather → Results with 3 combos.
  - Regenerate → Results whose combos include the 4th (previously unseen) combo, so not all three keys equal the first run's.
  - Regenerate again → still Results, not NoResults, with ≥1 combo (the cycle restarted).
  - A fresh session (confirm weather again) → the first-run results are the same as the very first run's. Same day and same seed means a deterministic order.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :features:recommendations:jvmTest`.
- [ ] **Step 3: Implement.** Pass `exclude`, `jitter` and `today` into the engine call, in both the AI and non-AI branches. Add the empty-after-exclusion retry, which runs once without exclusion and resets the session set.
- [ ] **Step 4: Verify**
  - `./gradlew :features:recommendations:jvmTest :features:recommendations:testAndroidHostTest :features:recommendations:compileCommonMainKotlinMetadata`
  - `./gradlew :features:recommendations:compileTestKotlinIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true`
  - `./gradlew test lint assembleFullDebug assembleFossDebug`, run as separate invocations if needed.

  All BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** with message `feat(recommendations): regenerate cycles through unseen outfits; daily first pick (#94)`.
- [ ] **Step 6: PR checkpoint** (the controller does this).
  - Push `feat/engine-ranking-fixes`, open the PR with `Part of #94`, and wait for CI.
  - Smoke-test on the **test device only**, full flavor, AI off:
    - Regenerate 3 times: each time shows a different first outfit, until the wardrobe's combos run out and the cycle restarts.
    - The 3 pages have different top/bottom pairs.
    - With shoes in the wardrobe, the top results include shoes.
  - Before/after screenshots go in the PR comment.
  - Wait for the owner to merge.
