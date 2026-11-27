# Taste learning: grouped cross-validation. Implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** repeated outfits can no longer leak between CV training and validation, and can no longer inflate the gate's evidence. Learning also requires at least 20 distinct outfits.

**Architecture:** `ChoiceStratum` carries the worn core as `choiceKey`. `TasteWeightTrainer` trims each fold's training set by the validation block's keys, computes the one-SE rule and gate statistics with a cluster-robust SE clustered by key, and gates in two stages (habit, then rules). The weights schema goes to v5.

**Tech stack:** Kotlin Multiplatform, `features/recommendations` commonMain; kotlin.test.

**Spec:** `docs/superpowers/specs/2026-11-27-grouped-cv-design.md` (D1–D6). Owner: approved with the `MIN_DISTINCT_CHOICES = 20` minimum.

## Global Constraints
- **The key:** `choiceKey: Set<Long>` holds the worn core's item ids: Top + Bottom, or the OnePiece. Optional items never enter it.
- **Grouped folds:** each expanding fold drops from training every stratum whose `choiceKey` appears in that fold's validation block. A trimmed training fold with fewer than `MIN_INITIAL_TRAIN_STRATA = 10` strata is skipped. If every fold is skipped, return DEFAULT. Time order and fold boundaries are otherwise unchanged.
- **Gate statistics (revised D3):** over the `N` scored validation strata, the mean is the plain per-stratum mean `d̄ = Σdᵢ / N` (still weighted by wears). The SE is cluster-robust, clustered by `choiceKey` over the `G` scored keys: `SE = sqrt(G/(G−1) · Σ_g (Σ_{i∈g}(dᵢ − d̄))²) / N`. At least `MIN_SCORED_CHOICES = 2` distinct keys must be scored, otherwise DEFAULT. These feed the one-SE rule, both `GATE_SE_MULTIPLIER = 2.0` gates, and `TasteTrainingResult.meanLogLikDelta` / `meanLogLikDeltaStandardError`.
- **Two-stage gate (D3c):** stage 1 cross-validates with only `historyLift` free (`ConditionalLogitOptimizer.fit(freeFeatures = ...)`; a feature that isn't free stays exactly at β₀) and is gated against DEFAULT. Stage 2 frees every feature and is gated on paired deltas against the stage-1 fold fits if stage 1 was accepted, otherwise against DEFAULT. The result is the full fit, else the habit-only fit, else DEFAULT; `selectedLambda` is the winning stage's, and `TasteTrainingResult.acceptedStage` (`NONE | HABIT | FULL`) records which.
- **Minimum:** `MIN_DISTINCT_CHOICES = 20`. Fewer distinct keys among the valid strata returns DEFAULT, with `selectedLambda = null`.
- **Schema:** `TASTE_WEIGHTS_SCHEMA_VERSION = "v5"`. Update the session-test watermark literal, and add a CHANGELOG Fixed entry.
- **Unchanged:** the drift bound `[β₀−3, β₀+6]`, the thin-data clamp, `LAMBDA_PATH`, and the builder's control sampling, apart from setting `choiceKey`.
- **Purity and test names:** commonMain stays pure. commonTest names use words only.
- **Gradle and commits:** one Gradle invocation at a time, in the foreground. Commits are signed; never bypass signing. Never stage `.claude/settings.json`.

## Review Focus
1. **A history where every validation choice was also worn in training,** for example 3 outfits rotated, has every fold trimmed below 10. The result is DEFAULT, with no crash and no division by zero. Test: `every fold trimmed below the minimum falls back to default`.
2. **A validation block with a single distinct key** has SE 0. The gate must then require mean > 0, and the 2·SE comparison must stay well defined. Test: `a single validation choice cannot pass on zero standard error`, which asserts DEFAULT when only one key is scored.
3. **One-piece and top-bottom cores built from overlapping items** get distinct keys. Test: `choice keys distinguish one-piece and top-bottom cores`.
4. **Duplicated validation strata** give the same mean and SE as appearing once. Test: `duplicate validation choices do not shrink the standard error`.

---

### Task 1: Grouped CV, cluster statistics, distinct-choice minimum, schema v5

**Files:**
- Modify:
  - `features/recommendations/src/commonMain/.../engine/learning/HistoricalStratumBuilder.kt`: `ChoiceStratum` gains `choiceKey`, and the builder sets it.
  - `.../engine/learning/TasteWeightTrainer.kt`: grouped folds, per-key statistics, the minimum, and KDocs.
  - `.../RecommendationViewModel.kt`: v5.
  - `RecommendationViewModelSessionTest`: the watermark.
  - `CHANGELOG.md`.
- Test:
  - `.../engine/learning/TasteWeightTrainerTest.kt`
  - `HistoricalStratumBuilderTest.kt`
  - `LearningOutcomeTest.kt` and `LearningFixtures.kt`, for the repeated-noise and repeated-favourites fixtures.
  - Any test that constructs `ChoiceStratum` directly. Give it a key; a `logId`-derived singleton set keeps old tests' semantics, meaning no repeats.

**Interfaces:**
- **Produces:**
  - `ChoiceStratum(chosenFeatures, controlFeatures, logId, chosenBaselineOffset, controlBaselineOffsets, choiceKey: Set<Long>)`;
  - `TasteWeightTrainer.MIN_DISTINCT_CHOICES = 20`;
  - `TasteTrainingResult` gains `acceptedStage: TasteTrainingStage` (`NONE | HABIT | FULL`); its mean is per stratum and its SE cluster-robust (revised D3);
  - `ConditionalLogitOptimizer.fit(..., freeFeatures: BooleanArray? = null)` (D3c).

- [ ] **Step 0: Record the baseline replay numbers.** Add a temporary `println`, uncommitted, to `ReplayCheckTest`. Record fixed and learned `hitRateAt10` and `heldOutLogLik` for both the planted and the no-planted fixture. Then revert the file.
- [ ] **Step 1: Write the failing tests.**
  - **D6 rows:** repeated noise, repeated favourites, fold hygiene, cluster SE, and the distinct-choice minimum.
  - **The four Review Focus tests.**
  - **Fold hygiene needs a test seam.** For example, expose `internal fun groupedFolds(...)`, which returns the trimmed training indices per fold. `trainOnStrata` must use the same function.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** the new behaviour tests fail. Record which already pass; those are regression guards.
- [ ] **Step 3: Implement** D1–D5.
- [ ] **Step 4: Run each of these, one at a time:**
  - `./gradlew :features:recommendations:jvmTest --console=plain -q`
  - `./gradlew :features:recommendations:compileCommonMainKotlinMetadata --console=plain -q`
  - `./gradlew :features:recommendations:compileTestKotlinIosSimulatorArm64 --console=plain -q`

  **Expected:** PASS, with every `ReplayCheckTest` assertion unchanged. Record the after numbers. If a replay floor or the planted learned-beats-fixed check fails, report DONE_WITH_CONCERNS with the numbers. Never loosen an assertion or tune constants.
- [ ] **Step 5: Commit** `fix(recommendations): grouped CV, cluster-robust gate, two-stage habit/rule acceptance (#142)`.

## Whole-branch verification (controller)
- **Full pre-PR command:** run the one in `app-android/CLAUDE.md`, which includes `iosCompileCheck`.
- **Device smoke:** on the test device, after the v5 retrain, read the stored `taste_weights`. Expected: DEFAULT, or rule features near 1. Hue must not sit at −2. Footwear and layering must be 1.0, and suggestions must still include shoes.
